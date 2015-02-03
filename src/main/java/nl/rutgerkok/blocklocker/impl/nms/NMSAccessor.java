package nl.rutgerkok.blocklocker.impl.nms;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.block.Sign;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * Implementation of methods required by
 * nl.rutgerkok.chestsignprotect.impl.NMSAccessor for Minecraft 1.7.8 and 1.7.9.
 *
 */
public final class NMSAccessor {

    private final static String nmsPrefix = "net.minecraft.server.v1_8_R1.";
    private final static String obcPrefix = "org.bukkit.craftbukkit.v1_8_R1.";

    static Object enumField(Class<Enum<?>> enumClass, String name) {
        try {
            Method valueOf = getMethod(Enum.class, "valueOf", Class.class, String.class);
            return invokeStatic(valueOf, enumClass, name);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Constructor<?> getConstructor(Class<?> clazz, Class<?>... paramTypes) {
        try {
            return clazz.getConstructor(paramTypes);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Field getField(Class<?> clazz, String name) {
        try {
            return clazz.getField(name);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Method getMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            return clazz.getMethod(name, parameterTypes);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Class<?> getNMSClass(String name) {
        try {
            return Class.forName(nmsPrefix + name);
        } catch (ClassNotFoundException e) {
            throw Throwables.propagate(e);
        }
    }

    static Class<Enum<?>> getNMSEnum(String name) {
        Class<?> clazz = getNMSClass(name);
        if (!clazz.isEnum()) {
            throw new IllegalArgumentException(clazz + " is not an enum");
        }
        @SuppressWarnings("unchecked")
        Class<Enum<?>> enumClazz = (Class<Enum<?>>) clazz;
        return enumClazz;
    }

    static Class<?> getOBCClass(String name) {
        try {
            return Class.forName(obcPrefix + name);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Object retrieve(Object on, Field field) {
        try {
            return field.get(on);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Object call(Object on, Method method, Object... parameters) {
        try {
            return method.invoke(on, parameters);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    static Object invokeStatic(Method method, Object... parameters) {
        return call(null, method, parameters);
    }

    static Object newInstance(Constructor<?> constructor, Object... params) {
        try {
            return constructor.newInstance(params);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }

    final Class<?> BlockPosition;
    final Constructor<?> BlockPosition_new;
    final Class<?> ChatComponentText;
    final Constructor<?> ChatComponentText_new;
    final Class<?> ChatHoverable;
    final Method ChatHoverable_getChatComponent;
    final Constructor<?> ChatHoverable_new;
    final Class<?> ChatModifier;
    final Method ChatModifier_getChatHoverable;
    final Constructor<?> ChatModifier_new;
    final Class<?> CraftChatMessage;
    final Method CraftChatMessage_fromComponent;
    final Class<?> CraftWorld;
    final Method CraftWorld_getHandle;
    final Class<Enum<?>> EnumHoverAction;
    final Object EnumHoverAction_SHOW_TEXT;
    final Class<?> IChatBaseComponent;
    final Method IChatBaseComponent_getChatModifier;
    final Method ChatModifier_setChatHoverable;
    final Class<?> TileEntitySign;
    final Field TileEntitySign_lines;
    final Class<?> WorldServer;
    final Method WorldServer_getTileEntity;

    public NMSAccessor() {
        BlockPosition = getNMSClass("BlockPosition");
        WorldServer = getNMSClass("WorldServer");
        ChatModifier = getNMSClass("ChatModifier");
        ChatHoverable = getNMSClass("ChatHoverable");
        IChatBaseComponent = getNMSClass("IChatBaseComponent");
        EnumHoverAction = getNMSEnum("EnumHoverAction");
        TileEntitySign = getNMSClass("TileEntitySign");
        ChatComponentText = getNMSClass("ChatComponentText");

        CraftWorld = getOBCClass("CraftWorld");
        CraftChatMessage = getOBCClass("util.CraftChatMessage");

        CraftWorld_getHandle = getMethod(CraftWorld, "getHandle");
        CraftChatMessage_fromComponent = getMethod(CraftChatMessage, "fromComponent", IChatBaseComponent);
        WorldServer_getTileEntity = getMethod(WorldServer, "getTileEntity", BlockPosition);
        IChatBaseComponent_getChatModifier = getMethod(IChatBaseComponent, "getChatModifier");
        ChatModifier_setChatHoverable = getMethod(ChatModifier, "setChatHoverable", ChatHoverable);
        ChatModifier_getChatHoverable = getMethod(ChatModifier, "i");
        ChatHoverable_getChatComponent = getMethod(ChatHoverable, "b");

        ChatComponentText_new = getConstructor(ChatComponentText, String.class);
        BlockPosition_new = getConstructor(BlockPosition, int.class, int.class, int.class);
        ChatModifier_new = getConstructor(ChatModifier);
        ChatHoverable_new = getConstructor(ChatHoverable, EnumHoverAction, IChatBaseComponent);

        TileEntitySign_lines = getField(TileEntitySign, "lines");

        EnumHoverAction_SHOW_TEXT = enumField(EnumHoverAction, "SHOW_TEXT");
    }

    private String chatComponentToString(Object chatComponent) {
        return (String) invokeStatic(CraftChatMessage_fromComponent, chatComponent);
    }

    Object getBlockPosition(Location location) {
        return newInstance(BlockPosition_new,
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }

    /**
     * Gets the stored {@link JSONObject}s. If the sign contains no extra data
     * at all, an empty {@link Optional} will be returned. Otherwise, all
     * non-null {@link JSONObject}s stored in the sign will be added to the
     * list.
     * 
     * @param sign
     *            The sign.
     * @return The extra data, or empty if not found.
     */
    public Optional<List<JSONObject>> getJsonData(Sign sign) {
        // Find sign
        Optional<?> nmsSign = toNmsSign(sign);
        if (!nmsSign.isPresent()) {
            return Optional.absent();
        }

        // Find strings stored in hovertext
        Optional<String> secretData = getSecretData(nmsSign.get());
        if (!secretData.isPresent()) {
            return Optional.absent();
        }

        // Parse and sanitize the sting
        Object data = JSONValue.parse(secretData.get());
        if (data instanceof JSONArray) {
            List<JSONObject> result = new ArrayList<JSONObject>();
            for (Object object : (JSONArray) data) {
                if (object instanceof JSONObject) {
                    result.add((JSONObject) object);
                }
            }
            return Optional.of(result);
        }
        return Optional.absent();
    }

    private Optional<String> getSecretData(Object tileEntitySign) {
        Object line = ((Object[]) retrieve(tileEntitySign, TileEntitySign_lines))[0];
        Object chatModifier = call(line, IChatBaseComponent_getChatModifier);
        if (chatModifier != null) {
            Object chatHoverable = call(chatModifier, ChatModifier_getChatHoverable);
            if (chatHoverable != null) {
                return Optional.of(chatComponentToString(call(chatHoverable, ChatHoverable_getChatComponent)));
            }
        }

        return Optional.absent();
    }

    /**
     * Sets the given JSON array on the sign. The JSON array can have as many
     * elements as you want, and can contain anything that can be serialized as
     * JSON.
     * 
     * @param sign
     *            The sign.
     * @param jsonArray
     *            The array to store.
     */
    public void setJsonData(Sign sign, JSONArray jsonArray) {
        Optional<?> nmsSign = toNmsSign(sign);
        if (!nmsSign.isPresent()) {
            throw new RuntimeException("No sign at " + sign.getLocation());
        }

        setSecretData(nmsSign.get(), jsonArray.toJSONString());
    }

    private void setSecretData(Object tileEntitySign, String data) {
        Object line = ((Object[]) retrieve(tileEntitySign, TileEntitySign_lines))[0];
        Object modifier = Objects.firstNonNull(call(line, IChatBaseComponent_getChatModifier), newInstance(ChatModifier_new));
        Object chatComponentText = newInstance(ChatComponentText_new, data);
        Object hoverable = newInstance(ChatHoverable_new, EnumHoverAction_SHOW_TEXT, chatComponentText);
        call(modifier, ChatModifier_setChatHoverable, hoverable);
    }

    private Optional<?> toNmsSign(Sign sign) {
        Location location = sign.getLocation();
        Object nmsWorld = call(location.getWorld(), CraftWorld_getHandle);

        Object tileEntity = call(nmsWorld, WorldServer_getTileEntity, getBlockPosition(location));
        if (!TileEntitySign.isInstance(tileEntity)) {
            return Optional.absent();
        }

        return Optional.of(tileEntity);
    }

}
