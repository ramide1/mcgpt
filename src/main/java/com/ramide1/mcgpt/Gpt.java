package com.ramide1.mcgpt;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Gpt implements CommandExecutor {
    private App plugin;

    public Gpt(App plugin) {
        this.plugin = plugin;
    }

    public String sendRequestToGPTApi(String url, String instructions, String sender, String question, String apikey,
            String model) {
        String content = "Url is empty.";
        if (!url.isEmpty()) {
            content = "Response was not ok.";
            try {
                HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                if (!apikey.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + apikey);
                }
                connection.setDoOutput(true);
                String messages = "{\"role\": \"user\",\"content\": \"" + question + "\"}";
                if (!getHistory(sender).isEmpty()) {
                    messages = getHistory(sender) + "," + messages;
                }
                String newHistory = messages;
                if (!instructions.isEmpty()) {
                    messages = "{\"role\": \"system\",\"content\": \"" + instructions + "\"}" + "," + messages;
                }
                messages = "[" + messages + "]";
                String data = !model.isEmpty() ? "{\"model\": \"" + model + "\", \"messages\": " + messages + "}"
                        : "{\"messages\": " + messages + "}";
                OutputStream os = connection.getOutputStream();
                byte[] postData = data.getBytes("utf-8");
                os.write(postData, 0, postData.length);
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    content = "Field content was not found in JSON response.";
                    BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line);
                    }
                    String regex = "\"content\":\"([^\"]+)\"";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(response.toString());
                    if (matcher.find()) {
                        content = matcher.group(1);
                        content = content.replace("\n", " ");
                        newHistory = newHistory + "," + "{\"role\": \"assistant\",\"content\": \"" + content + "\"}";
                        saveHistory(sender, newHistory);
                    }
                }
                connection.disconnect();
            } catch (Exception e) {
                content = e.getMessage();
            }
        }
        return content;
    }

    public boolean onCommand(CommandSender sender, Command gpt, String label, String[] args) {
        String url = plugin.getConfig().getString("Config.url", "");
        String instructions = plugin.getConfig().getString("Config.instructions", "");
        String apikey = plugin.getConfig().getString("Config.apikey", "");
        String model = plugin.getConfig().getString("Config.model", "");
        if (sender instanceof Player) {
            Player player = (Player) sender;
            if (args.length >= 1) {
                StringBuilder question = new StringBuilder();
                for (String arg : args) {
                    question.append(arg).append(" ");
                }
                question.deleteCharAt(question.length() - 1);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        String response = sendRequestToGPTApi(url, instructions, player.getName(),
                                question.toString(), apikey,
                                model);
                        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(response));
                    }
                }.runTaskAsynchronously(plugin);
            } else {
                player.sendMessage(ChatColor.RED + "This command needs at least one argument.");
            }
        } else {
            if (args.length >= 1) {
                StringBuilder question = new StringBuilder();
                for (String arg : args) {
                    question.append(arg).append(" ");
                }
                question.deleteCharAt(question.length() - 1);
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        String response = sendRequestToGPTApi(url, instructions, "console",
                                question.toString(),
                                apikey,
                                model);
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.getLogger().info(response));
                    }
                }.runTaskAsynchronously(plugin);
            } else {
                plugin.getLogger().info(ChatColor.RED + "This command needs at least one argument.");
            }
        }
        return true;
    }

    public boolean saveHistory(String sender, String history) {
        plugin.dataConfig.set(sender, history);
        try {
            plugin.dataConfig.save(plugin.data);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public String getHistory(String sender) {
        return plugin.dataConfig.contains(sender) ? plugin.dataConfig.getString(sender) : "";
    }
}