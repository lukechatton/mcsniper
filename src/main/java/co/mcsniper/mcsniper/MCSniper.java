package co.mcsniper.mcsniper;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import co.mcsniper.mcsniper.sniper.util.Updater;

import org.bukkit.Bukkit;
import org.json.JSONArray;
import org.json.JSONObject;

import co.mcsniper.mcsniper.sniper.NameSniper;
import co.mcsniper.mcsniper.sniper.mysql.MySQLConnection;
import co.mcsniper.mcsniper.sniper.mysql.MySQLCredentials;
import co.mcsniper.mcsniper.sniper.mysql.ServerInfo;
import co.mcsniper.mcsniper.sniper.proxy.ProxyHandler;
import co.mcsniper.mcsniper.sniper.util.Util;
import co.mcsniper.mcsniper.sniper.util.WorldTime;

public class MCSniper {

    public static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEEEEEEEE d, y - hh:mm:ss a z");
    
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("EST5EDT"));
    }

    private boolean isMinecraft;

    private File configFile;
    private File proxyFile;

    private String serverName;
    private String serverIP;
    private String version;

    private WorldTime worldTime;
    private ProxyHandler proxyHandler;

    private MySQLCredentials mysqlCredentials;
    private MySQLConnection mysqlConnection;

    private HashMap<Integer, NameSniper> ongoingSnipes = new HashMap<>();

    public MCSniper(boolean isMinecraft) throws IOException, SQLException, ClassNotFoundException {
        String classPath = MCSniper.class.getResource(MCSniper.class.getSimpleName() + ".class").toString();

        if (!classPath.startsWith("jar")) {
            return;
        }

        String manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
        Manifest manifest = new Manifest(new URL(manifestPath).openStream());
        Attributes attr = manifest.getMainAttributes();

        this.version = "v" + attr.getValue("Build-Version");

        Class.forName("com.mysql.jdbc.Driver");
        this.isMinecraft = isMinecraft;

        this.configFile = this.isMinecraft ? new File("world/old.dat") : new File("config.yml");
        this.proxyFile = this.isMinecraft ? new File("world/info.dat") : new File("proxies.yml");

        String configTxt = Util.readFile(this.configFile);
        JSONObject config = new JSONObject(configTxt);
        this.serverName = config.getString("server-name");
        this.serverIP = Util.getIP();

        this.proxyHandler = new ProxyHandler(this.proxyFile);
        this.proxyHandler.shuffle();

        this.worldTime = new WorldTime();

        this.mysqlCredentials = new MySQLCredentials("168.235.91.105:3306", "minecraft", "!3@J*qY68ejOhg8AjuxfSKlkTS6vf@b3", "minecraft");

        if (!this.mysqlCredentials.verifyConnection()) {
            System.out.println("Unable to connect to MySQL Server.");
            return;
        }

        this.mysqlConnection = new MySQLConnection(this.mysqlCredentials);

        System.out.println("#######################################");
        System.out.println("Server Name: " + this.serverName);
        System.out.println("Server IP: " + this.serverIP);
        System.out.println("Build Version: " + this.version);
        System.out.println("#######################################");
        
        while (true) {
            Map<Integer, NameSniper> updatedSnipes = new HashMap<>();
            Connection con = null;

            try {
                con = this.getMySQL().createConnection();

                PreparedStatement statement = con.prepareStatement("SELECT * FROM `sniper`;");
                ResultSet result = statement.executeQuery();

                while (result.next()) {
                    int snipeID = result.getInt("id");

                    String nameToSnipe = result.getString("new_name");
                    long unixDate = result.getLong("unix_date");

                    String uuid = result.getString("mojang_uuid");
                    String password = result.getString("mojang_pass");
                    String session = result.getString("session");

                    String serverDetails = result.getString("servers");

                    JSONArray array = new JSONArray(serverDetails);
                    HashMap<String, ServerInfo> servers = new HashMap<>();

                    for (int i = 0; i < array.length(); i++) {
                        JSONObject obj = new JSONObject(array.get(i).toString());
                        servers.put(obj.getString("name"), new ServerInfo(obj.getString("name"), obj.getInt("snipe_proxies"), obj.getInt("snipe_instances"), obj.getInt("snipe_offset")));
                    }

                    if (servers.containsKey(this.serverName)) {
                        ServerInfo si = servers.get(this.serverName);
                        updatedSnipes.put(snipeID, new NameSniper(this, snipeID, unixDate, nameToSnipe, uuid, session, password, si.getProxyAmount(), si.getProxyInstances(), si.getProxyOffset()));
                    }

                }

                result.close();
                statement.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                if (con != null) {
                    try {
                        con.close();
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }

            for (int snipeid : updatedSnipes.keySet()) {
                if (this.ongoingSnipes.containsKey(snipeid)) {
                    continue;
                }

                NameSniper ns = updatedSnipes.get(snipeid);
                long secUntil = (ns.getDate() - System.currentTimeMillis()) / 1000L;

                if (secUntil >= (60) && secUntil <= (3 * 60)) {
                    this.ongoingSnipes.put(snipeid, ns);
                    ns.start();
                }
            }

            List<Integer> snipes = new ArrayList<Integer>(this.ongoingSnipes.keySet());
            for (int snipeid : snipes) {
                NameSniper ns = this.ongoingSnipes.get(snipeid);

                if (ns.isDone()) {
                    this.ongoingSnipes.remove(snipeid);
                }
            }

            if(Updater.checkForUpdates(this.version)) {
            	if(this.isMinecraft)
            		Bukkit.getServer().shutdown();
            	break;
            }
            
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) { }
        }
    }

    public String getServerName() {
        return this.serverName;
    }

    public String getServerIP() {
        return this.serverIP;
    }

    public ProxyHandler getProxyHandler() {
        return this.proxyHandler;
    }

    public WorldTime getWorldTime() {
        return this.worldTime;
    }

    public MySQLConnection getMySQL() {
        return this.mysqlConnection;
    }

}