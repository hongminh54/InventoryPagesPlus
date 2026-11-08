package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.storage.PlayerInventoryDatabase;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

public class ProtectionManager {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 60000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Normalizes a command to a lowercase root name: strips a leading '/' and any 'namespace:' prefix.
    public static String normalizeCommand(String raw) {
        String command = raw.trim().toLowerCase(Locale.ROOT);
        if (command.startsWith("/"))
            command = command.substring(1);
        int namespace = command.indexOf(':');
        if (namespace >= 0)
            command = command.substring(namespace + 1);
        return command;
    }

    public static boolean isValidPin(String pin) {
        if (pin == null)
            return false;

        int min = Math.max(1, Settings.PROTECTION_PIN_MIN_LENGTH);
        int max = Math.max(min, Settings.PROTECTION_PIN_MAX_LENGTH);
        if (pin.length() < min || pin.length() > max)
            return false;

        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9')
                return false;
        }
        return true;
    }

    public static String hashPin(String pin) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] hash = factory.generateSecret(spec).getEncoded();
            return ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash the protection PIN.", exception);
        } finally {
            spec.clearPassword();
        }
    }

    public static boolean verifyPin(String pin, String storedHash) {
        if (pin == null || storedHash == null || storedHash.isEmpty())
            return false;

        String[] parts = storedHash.split(":");
        if (parts.length != 3)
            return false;

        int iterations;
        try {
            iterations = Integer.parseInt(parts[0]);
        } catch (NumberFormatException exception) {
            return false;
        }
        if (iterations <= 0)
            return false;

        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[1]);
            expected = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException exception) {
            return false;
        }

        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, iterations, expected.length * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(ALGORITHM);
            byte[] actual = factory.generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception exception) {
            return false;
        } finally {
            spec.clearPassword();
        }
    }

    public static boolean isBanned(PlayerInventoryDatabase data) {
        return getRemainingBanSeconds(data) > 0;
    }

    public static long getRemainingBanSeconds(PlayerInventoryDatabase data) {
        long remainingMillis = data.getProtectionBanUntil() - System.currentTimeMillis();
        if (remainingMillis <= 0)
            return 0;
        return (remainingMillis + 999) / 1000;
    }

    // Returns true when this failure just triggered a ban.
    public static boolean registerFailedAttempt(PlayerInventoryDatabase data) {
        int attempts = data.getProtectionAttempts() + 1;

        if (attempts >= Math.max(1, Settings.PROTECTION_MAX_ATTEMPTS)) {
            long banSeconds = Math.max(0L, Settings.PROTECTION_BAN_SECONDS);
            if (banSeconds > 0) {
                data.setProtectionBanUntil(System.currentTimeMillis() + banSeconds * 1000L);
                // After a ban the player may try again from scratch.
                data.setProtectionAttempts(0);
                return true;
            }
        }

        data.setProtectionAttempts(attempts);
        return false;
    }

    // Only call while LOCKED; absorbs the real items sitting in the display slots before showing the page.
    // Returns true when at least one item had to be dropped.
    public static boolean unlock(PlayerInventoryDatabase data) {
        Player player = data.getPlayer();

        List<ItemStack> absorbed = new ArrayList<>();
        if (player != null) {
            for (int slot = 9; slot < 36; slot++) {
                if (!Settings.SKIP_SLOTS.isEmpty() && Settings.SKIP_SLOTS.contains(slot - 9))
                    continue;
                ItemStack item = player.getInventory().getItem(slot);
                if (item != null) {
                    absorbed.add(item);
                    player.getInventory().setItem(slot, null);
                }
            }
        }

        // Flip to unlocked before storing/showing so the data-layer guards no longer block.
        data.setProtectionUnlocked(true);
        data.setProtectionAttempts(0);
        data.setProtectionBanUntil(0);

        if (player == null)
            return false;

        GameMode gameMode = player.getGameMode();
        // Render the saved page first so storeOrDropItem's internal saveCurrentPage reads the real
        // page items, not the emptied display slots (which would wipe the current page).
        data.showPage(gameMode);

        boolean dropped = false;
        for (ItemStack item : absorbed) {
            if (data.storeOrDropItem(item, gameMode))
                dropped = true;
        }
        data.showPage(gameMode);
        return dropped;
    }

    public static void lock(PlayerInventoryDatabase data) {
        if (!data.hasProtectionPin())
            return;

        // Save while still unlocked, otherwise the guard would skip it and the displayed page would be lost.
        data.saveCurrentPage();
        data.setProtectionUnlocked(false);
        data.clearDisplayedPage();
    }

}