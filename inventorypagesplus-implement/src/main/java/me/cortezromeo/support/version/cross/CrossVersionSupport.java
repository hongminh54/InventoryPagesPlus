package me.cortezromeo.support.version.cross;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.cortezromeo.inventorypagesplus.server.VersionSupport;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.bukkit.ChatColor.COLOR_CHAR;

public class CrossVersionSupport extends VersionSupport {

    // Accepted hex formats: &#RRGGBB, #RRGGBB, <#RRGGBB>, {#RRGGBB}
    private static final Pattern HEX_PATTERN = Pattern.compile("[<&{]?#([0-9a-fA-F]{6})[>}]?");
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i)[&\\u00A7][0-9A-FK-ORX]");
    private static final String MATERIAL_JAVADOC = "https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Material.html";
    private static final String SOUND_JAVADOC = "https://hub.spigotmc.org/javadocs/spigot/org/bukkit/Sound.html";

    private final NamespacedKey customDataKey;

    public CrossVersionSupport(Plugin plugin) {
        super(plugin);
        this.customDataKey = new NamespacedKey(plugin, "customdata");
    }

    @Override
    public ItemStack getItemStack(ItemStack itemStack) {
        if (isEmpty(itemStack))
            return null;

        // A clone keeps every component of the current server version, while rebuilding the stack
        // from a material table would silently drop items that were added after the library was released.
        return itemStack.clone();
    }

    @Override
    public ItemStack createItemStack(String material, int amount, short data, int customModelData) {
        ItemStack itemStack = matchItemStack(material, data);
        if (itemStack == null) {
            logUnknown("MATERIAL", material, MATERIAL_JAVADOC);
            return new ItemStack(Material.BEDROCK);
        }

        itemStack.setAmount(amount);

        if (customModelData != 0) {
            ItemMeta itemMeta = itemStack.getItemMeta();
            if (itemMeta != null) {
                itemMeta.setCustomModelData(customModelData);
                itemStack.setItemMeta(itemMeta);
            }
        }
        return itemStack;
    }

    @Override
    public Sound createSound(String soundName) {
        Sound sound = matchSound(soundName);
        if (sound == null) {
            logUnknown("SOUND", soundName, SOUND_JAVADOC);
            return XSound.BLOCK_AMETHYST_CLUSTER_BREAK.parseSound();
        }
        return sound;
    }

    @Override
    public ItemStack getHeadItemFromPlayerName(String playerName) {
        // A name only profile is filled in by the server itself, so heads of real players never
        // need version specific profile injection.
        return createHead(Bukkit.createPlayerProfile(playerName));
    }

    @Override
    public ItemStack getHeadItemFromBase64(String headValue) {
        try {
            return createHead(textureProfileOf(headValue));
        } catch (Exception exception) {
            getPlugin().getLogger().warning("Unable to read the custom head value: " + headValue);
            exception.printStackTrace();
            return new ItemStack(Material.PLAYER_HEAD);
        }
    }

    @Override
    public ItemStack addCustomData(ItemStack itemStack, String data) {
        if (isEmpty(itemStack))
            return null;

        ItemStack customItemStack = itemStack.clone();
        ItemMeta itemMeta = customItemStack.getItemMeta();
        if (itemMeta == null)
            return null;

        itemMeta.getPersistentDataContainer().set(customDataKey, PersistentDataType.STRING, data);
        customItemStack.setItemMeta(itemMeta);
        return customItemStack;
    }

    @Override
    public String getCustomData(ItemStack itemStack) {
        // Items without meta never carry custom data, and checking that first keeps the
        // per-click and per-save paths free of ItemMeta copies.
        if (isEmpty(itemStack) || !itemStack.hasItemMeta())
            return "";

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null)
            return "";

        return itemMeta.getPersistentDataContainer().getOrDefault(customDataKey, PersistentDataType.STRING, "");
    }

    @Override
    public String addColor(String textToTranslate) {
        if (textToTranslate == null)
            return "NULL";

        Matcher matcher = HEX_PATTERN.matcher(textToTranslate);
        StringBuilder buffer = new StringBuilder(textToTranslate.length());
        while (matcher.find()) {
            String group = matcher.group(1);
            // Legacy hex codes are understood by every supported version and by inventory titles.
            matcher.appendReplacement(buffer, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5)
            );
        }
        String hexTranslated = matcher.appendTail(buffer).toString();

        return ChatColor.translateAlternateColorCodes('&', hexTranslated);
    }

    @Override
    public String stripColor(String textToStrip) {
        if (textToStrip == null)
            return null;

        String withoutHex = HEX_PATTERN.matcher(textToStrip).replaceAll("");
        return LEGACY_COLOR_PATTERN.matcher(ChatColor.stripColor(withoutHex)).replaceAll("");
    }

    private ItemStack createHead(PlayerProfile profile) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta itemMeta = head.getItemMeta();
        if (!(itemMeta instanceof SkullMeta skullMeta))
            return head;

        skullMeta.setOwnerProfile(profile);
        head.setItemMeta(skullMeta);
        return head;
    }

    /**
     * The base64 value copied from minecraft-heads.com only wraps the texture url, and the profile
     * API is given that url instead of a raw property, which keeps heads free of reflection.
     */
    private PlayerProfile textureProfileOf(String headValue) throws MalformedURLException {
        JsonObject skin = JsonParser.parseString(new String(Base64.getDecoder().decode(headValue), StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN");

        // A texture only head is identified by a stable id built from its own value.
        PlayerProfile profile = Bukkit.createPlayerProfile(UUID.nameUUIDFromBytes(headValue.getBytes(StandardCharsets.UTF_8)));
        PlayerTextures textures = profile.getTextures();
        URL skinUrl = URI.create(skin.get("url").getAsString()).toURL();
        String skinModel = skin.has("model") ? skin.get("model").getAsString() : null;
        if ("slim".equalsIgnoreCase(skinModel))
            textures.setSkin(skinUrl, PlayerTextures.SkinModel.SLIM);
        else
            textures.setSkin(skinUrl);

        return profile;
    }

    /**
     * The server registry is asked first because it always knows the materials of the version that is
     * running, XSeries is only the fallback for legacy "id:data" values and renamed materials.
     */
    private ItemStack matchItemStack(String material, short data) {
        if (material == null || material.isEmpty())
            return null;

        ItemStack itemStack = toPlainItemStack(Material.matchMaterial(material));
        if (itemStack != null)
            return itemStack;

        // A "data" value only means something to the pre-flattening "id:data" notation.
        String legacyMaterial = data == 0 || material.indexOf(':') >= 0 ? material : material + ":" + data;
        return XMaterial.matchXMaterial(legacyMaterial).map(XMaterial::parseItem).orElse(null);
    }

    /**
     * Sound constants of the running server win over XSound for the same reason materials do.
     */
    private Sound matchSound(String soundName) {
        if (soundName == null || soundName.isEmpty())
            return null;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT)
                    .replace(' ', '_').replace('.', '_'));
            if (sound != null)
                return sound;
        } catch (IllegalArgumentException ignored) {
            // Not a sound constant of this version, XSeries may still know its previous name.
        }

        try {
            return XSound.matchXSound(soundName).map(XSound::parseSound).orElse(null);
        } catch (Exception exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private ItemStack toPlainItemStack(Material material) {
        if (material == null || material.isLegacy() || !material.isItem())
            return null;
        return new ItemStack(material);
    }

    private boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    private void logUnknown(String type, String name, String javadoc) {
        Logger logger = getPlugin().getLogger();
        logger.severe("----------------------------------------------------");
        logger.severe(type + " NAME " + name + " DOES NOT EXIST!");
        logger.severe("Maybe you type it wrong or it does not exist in this server version.");
        logger.severe("Please take a look at those valid " + type.toLowerCase(Locale.ROOT) + "s right here:");
        logger.severe(javadoc);
        logger.severe("----------------------------------------------------");
    }
}
