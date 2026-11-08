package me.cortezromeo.inventorypagesplus;

import me.cortezromeo.inventorypagesplus.util.MessageUtil;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// for selling plugin at dihoastore.net
public class DiHoaStore {
    private static final String productName = "InventoryPagesPlus";

    public static boolean doDiHoa() {
        MessageUtil.log("&f--------------------------------");
        MessageUtil.log("&f  _                                                      _");
        MessageUtil.log("&f (_)_ ____   __    _ __   __ _  __ _  ___  ___     _ __ | |_   _ ___");
        MessageUtil.log("&f | | '_ \\ \\ / /   | '_ \\ / _` |/ _` |/ _ \\/ __|   | '_ \\| | | | / __|");
        MessageUtil.log("&f | | | | \\ V /    | |_) | (_| | (_| |  __/\\__ \\   | |_) | | |_| \\__ \\");
        MessageUtil.log("&f |_|_| |_|\\_(_)   | .__/ \\__,_|\\__, |\\___||___/   | .__/|_|\\__,_|___/");
        MessageUtil.log("&f                  |_|          |___/              |_|");
        MessageUtil.log("");
        MessageUtil.log("       &dĐang xác minh license key dihoastore...");
        MessageUtil.log("");
        MessageUtil.log("&f--------------------------------");

        if (DiHoaStore.KeyStatus(InventoryPagesPlus.plugin.getConfig().getString("license-key"))) {
            if (DiHoaStore.action(InventoryPagesPlus.plugin.getConfig().getString("license-key"), "enable")) {
                MessageUtil.log("&f--------------------------------");
                MessageUtil.log("&f  _                                                      _");
                MessageUtil.log("&f (_)_ ____   __    _ __   __ _  __ _  ___  ___     _ __ | |_   _ ___");
                MessageUtil.log("&f | | '_ \\ \\ / /   | '_ \\ / _` |/ _` |/ _ \\/ __|   | '_ \\| | | | / __|");
                MessageUtil.log("&f | | | | \\ V /    | |_) | (_| | (_| |  __/\\__ \\   | |_) | | |_| \\__ \\");
                MessageUtil.log("&f |_|_| |_|\\_(_)   | .__/ \\__,_|\\__, |\\___||___/   | .__/|_|\\__,_|___/");
                MessageUtil.log("&f                  |_|          |___/              |_|");
                MessageUtil.log("");
                MessageUtil.log("       &2Xác minh license key thành công, cảm ơn bạn đã ủng hộ plugin!");
                MessageUtil.log("");
                MessageUtil.log("&f--------------------------------");
                return true;
            }
        } else {
            MessageUtil.log("&f--------------------------------");
            MessageUtil.log("&4  _                                                      _");
            MessageUtil.log("&4 (_)_ ____   __    _ __   __ _  __ _  ___  ___     _ __ | |_   _ ___");
            MessageUtil.log("&4 | | '_ \\ \\ / /   | '_ \\ / _` |/ _` |/ _ \\/ __|   | '_ \\| | | | / __|");
            MessageUtil.log("&4 | | | | \\ V /    | |_) | (_| | (_| |  __/\\__ \\   | |_) | | |_| \\__ \\");
            MessageUtil.log("&4 |_|_| |_|\\_(_)   | .__/ \\__,_|\\__, |\\___||___/   | .__/|_|\\__,_|___/");
            MessageUtil.log("&4                  |_|          |___/              |_|");
            MessageUtil.log("");
            MessageUtil.log("       &eThiếu license key!");
            MessageUtil.log("");
            MessageUtil.log("&cVUI LÒNG GHI LICENSE KEY ĐÃ MUA TẠI DIHOASTORE.NET vào config.yml");
            MessageUtil.log("");
            MessageUtil.log("&f--------------------------------");
            return false;
        }
        return false;
    }

    public static boolean KeyStatus(String key) {
        try {
            if (getIpaddress() == null)
                return false;
            String query = key + "!K04!" + getIpaddress();
            HttpURLConnection connection = (HttpURLConnection) (new URL("https://dihoastore-mc.tk/api/api.php?query=" + getMd5(query) + "&plugin=" + productName + "&license=" + key + "&ip=" + getIpaddress())).openConnection();
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11");
            connection.setUseCaches(false);
            connection.setAllowUserInteraction(false);
            connection.setConnectTimeout(2000);
            connection.connect();
            BufferedReader stream = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String inputLine;
            while ((inputLine = stream.readLine()) != null)
                sb.append(inputLine);
            stream.close();
            connection.disconnect();
            String json = sb.toString().replace(" ", "");
            if (json != null) {
                JSONParser parser = new JSONParser();
                JSONObject object = (JSONObject) parser.parse(json);
                if (object.get("error").toString().equals("yes"))
                    return false;
                if (object.get("error").toString().equals("no")) {
                    return object.get("status").toString() != "online";
                }
                return false;
            }
            MessageUtil.log("&e[DIHOASTORE&f-&cInventoryPagesPlus&e] §fLooks like the product key is working. Please visit our website and go to purchased plugin then click to reset key then reload this plugin!");
            return false;
        } catch (IOException | org.json.simple.parser.ParseException e) {
            MessageUtil.log("&e[DIHOASTORE&f-&cInventoryPagesPlus&e] License key đã hoạt dộng, nhưng có vẻ như nó đang được sử dụng trong một máy chủ khác. Nếu bạn đã mua và sử dụng plugin InventoryPagesPlus trên nhiều máy chủ, vui lòng liên hệ discord &bcortez_romeo&e để mình đưa bản custom xài được trên đa máy chủ. Nếu bạn sử dụng plugin cho rieng một máy chủ duy nhất, vui lòng vào &bhttps://dihoastore.net/account/index.php&e và nhấn vào nút &bRESET KEY&e để lấy key mới, sau đó nhập key mới vào lại config.yml");
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean action(String key, String action) {
        try {
            if (getIpaddress() == null)
                return false;
            String query = key + "!K04!" + getIpaddress();
            URL url = new URL("https://dihoastore-mc.tk/api/api.php?query=" + getMd5(query) + "&plugin=" + productName + "&license=" + key + "&action=" + action + "&ip=" + getIpaddress());
            BufferedReader stream = new BufferedReader(new InputStreamReader(url.openStream()));
            StringBuilder sb = new StringBuilder();
            String inputLine;
            while ((inputLine = stream.readLine()) != null)
                sb.append(inputLine);
            stream.close();
            String json = sb.toString().replace(" ", "");
            if (json != null) {
                JSONParser parser = new JSONParser();
                JSONObject object = (JSONObject) parser.parse(json);
                if (object.get("error").toString().equals("yes"))
                    return false;
                if (action.equals("enable")) {
                    return object.get("status").toString().equals("offline");
                }
                return object.get("status").toString().equals("online");
            }
            return false;
        } catch (IOException | org.json.simple.parser.ParseException e) {
            MessageUtil.log("&e[DIHOASTORE&f-&cInventoryPagesPlus&e] License key đã hoạt dộng, nhưng có vẻ như nó đang được sử dụng trong một máy chủ khác. Nếu bạn đã mua và sử dụng plugin InventoryPagesPlus trên nhiều máy chủ, vui lòng liên hệ discord &bcortez_romeo&e để mình đưa bản custom xài được trên đa máy chủ. Nếu bạn sử dụng plugin cho rieng một máy chủ duy nhất, vui lòng vào &bhttps://dihoastore.net/account/index.php&e và nhấn vào nút &bRESET KEY&e để lấy key mới, sau đó nhập key mới vào lại config.yml");
            return false;
        }
    }

    public static String getMd5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);
            String hashtext = no.toString(16);
            while (hashtext.length() < 32)
                hashtext = "0" + hashtext;
            return hashtext;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getIpaddress() {
        String ip = null;
        try {
            URL whatismyip = new URL("http://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            ip = in.readLine();
        } catch (Exception ex) {
            MessageUtil.log("&e[DIHOASTORE&f-&cInventoryPagesPlus&e] Không thể lấy địa chỉ IP!");
        }
        return ip;
    }
}
