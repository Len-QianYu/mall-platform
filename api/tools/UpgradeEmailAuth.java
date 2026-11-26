import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.Properties;

// 在 api/ 下使用 Java 21 源文件启动，只执行同目录项目的邮箱登录升级脚本。
public class UpgradeEmailAuth {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String host = setting(properties, "DB_HOST", "localhost");
        String port = setting(properties, "DB_PORT", "3306");
        String database = setting(properties, "DB_NAME", "mall_platform");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
            + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        String sql = Files.readString(Path.of("src/main/resources/db/upgrade-email-auth.sql"), StandardCharsets.UTF_8);
        try (var connection = DriverManager.getConnection(url,
            setting(properties, "DB_USERNAME", "root"), setting(properties, "DB_PASSWORD", ""))) {
            for (String command : sql.split(";")) {
                if (!command.isBlank()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute(command);
                    }
                }
            }
        }
        System.out.println("Email authentication schema upgrade completed.");
    }

    private static String setting(Properties properties, String name, String fallback) {
        String value = System.getenv(name);
        return value == null ? properties.getProperty(name, fallback) : value;
    }
}
