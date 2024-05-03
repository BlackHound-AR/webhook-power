package minecraft.webhook;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;

import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

@Plugin(
        id = "webhook",
        name = "WebHook",
        version = "1.0-SNAPSHOT"
)
public class WebHook {

    private final Logger logger;
    private final ProxyServer proxy;
    private Optional<Timer> emptyServerTimer = Optional.empty();

    @Inject
    public WebHook(Logger logger, ProxyServer proxy) {
        this.logger = logger;
        this.proxy = proxy;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("¡Plugin WebHook cargado correctamente!");

        // Crear la carpeta y el archivo de configuración al inicializar
        try {
            createConfigFolderAndFile();
        } catch (IOException e) {
            logger.error("Error al crear la carpeta y el archivo de configuración.", e);
        }
    }

    @Subscribe
    public void onPlayerLogin(PostLoginEvent event) {
        String playerName = event.getPlayer().getUsername();
        int playerCount = proxy.getPlayerCount(); // Obtiene la cantidad total de jugadores conectados
        String webhookUrl = getConfiguredWebhookUrl();

        // Verifica si el servidor "lobby" está desconectado y vacío
        proxy.getServer("lobby").ifPresent(lobby -> {
            if (lobby.getPlayersConnected().isEmpty()) {
                // Si no hay jugadores conectados, inicia un temporizador si aún no está activo
                if (!emptyServerTimer.isPresent()) {
                    emptyServerTimer = Optional.of(new Timer());
                    emptyServerTimer.get().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            // Envía el mensaje después de 30 minutos
                            sendEmptyServerWebhook(webhookUrl);
                        }
                    }, 30 * 60 * 1000); // 30 minutos en milisegundos
                }
            } else {
                // Si hay jugadores conectados, cancela el temporizador si está activo
                emptyServerTimer.ifPresent(Timer::cancel);
                emptyServerTimer = Optional.empty();
            }
        });

        // Envía el mensaje cuando un jugador se conecta
        sendPlayerConnectedWebhook(webhookUrl, playerName, playerCount);
    }
    private void createConfigFolderAndFile() throws IOException {
        Optional<PluginContainer> pluginContainer = proxy.getPluginManager().getPlugin("webhook");
        Path pluginDir;
        if (pluginContainer.isPresent()) {
            Optional<Path> pluginPath = pluginContainer.get().getDescription().getSource().map(Path::getParent);
            if (pluginPath.isPresent()) {
                pluginDir = pluginPath.get();
                // Continuar con el resto de la lógica aquí
                // ...
            } else {
                throw new IOException("No se pudo obtener el directorio del plugin.");
            }
        } else {
            throw new IOException("No se encontró el plugin 'webhook'.");
        }
        // Crear la carpeta "WebHook Power" si no existe
        Path webhookDir = pluginDir.resolve("WebHook Power");
        if (!Files.exists(webhookDir)) {
            Files.createDirectories(webhookDir);
        }

        // Crear el archivo config.yml si no existe
        Path configFile = webhookDir.resolve("config.yml");
        if (!Files.exists(configFile)) {
            // Puedes personalizar el contenido del archivo según tus necesidades
            String configContent = "webhook_url: https://n8n.luzdelossiglos.org.ar/webhook-test/af8b6014-368e-4acb-9b04-225aebd63a72";
            Files.writeString(configFile, configContent);
        }
    }

    private String getConfiguredWebhookUrl() {
        // Aquí deberías leer el archivo config.yml y obtener la URL del webhook configurada
        // Por simplicidad, este método devuelve la URL predeterminada
        return "https://n8n.luzdelossiglos.org.ar/webhook-test/af8b6014-368e-4acb-9b04-225aebd63a72";
    }
    private void sendPlayerConnectedWebhook(String webhookUrl, String playerName, int playerCount) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"¡El jugador " + playerName + " se ha conectado! Total de jugadores: " + playerCount + "\"}"))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                logger.info("Notificación enviada correctamente.");
            } else {
                logger.error("Error al enviar la notificación: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void sendEmptyServerWebhook(String webhookUrl) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"content\":\"El servidor está vacío desde hace 30 minutos.\"}"))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                logger.info("Notificación de servidor vacío enviada correctamente.");
            } else {
                logger.error("Error al enviar la notificación de servidor vacío: " + response.statusCode());
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
