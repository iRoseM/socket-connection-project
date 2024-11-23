import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 6789;
    public static List<ClientHandler> clients = new CopyOnWriteArrayList<>();
 
    public static List<String> playingClients = new CopyOnWriteArrayList<>();
    public static Map<String, Integer> playerScores = new ConcurrentHashMap<>();
    public static String[] questions = {
        "Red + Yellow = ?", "Blue + Yellow = ?", "Red + Blue = ?", "Red + White = ?",
        "Yellow + Green = ?", "Blue + White = ?", "Green + Red = ?", "Orange + Blue = ?",
        "Yellow + Blue + Red = ?", "Green + Yellow = ?"
    };
    public static String[] answers = {
        "Orange", "Green", "Purple", "Pink", "Lime",
        "Light Blue", "Brown", "Gray", "Brown", "Chartreuse"
    };
    public static int currentQuestionIndex = 0;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server running on port: " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
                broadcastConnectedPlayers();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static synchronized void broadcastQuestion() {
        if (currentQuestionIndex < questions.length) {
            StringBuilder questionMessage = new StringBuilder("QUESTION:").append(questions[currentQuestionIndex])
                .append(";OPTIONS:")
                .append(String.join(",", answers));
            clients.parallelStream()
                .filter(client -> playingClients.contains(client.getUsername()))
                .forEach(client -> client.sendMessage(questionMessage.toString()));

            // Start a timer for the question
            startQuestionTimer();
        }
    }

    public static synchronized void broadcastScores() {
        StringBuilder scoresMessage = new StringBuilder("SCORES:");
        playingClients.forEach(player -> scoresMessage.append(player).append("=").append(playerScores.getOrDefault(player, 0)).append(","));
        if (scoresMessage.length() > 7) scoresMessage.setLength(scoresMessage.length() - 1);
        clients.parallelStream()
            .filter(client -> playingClients.contains(client.getUsername()))
            .forEach(client -> client.sendMessage(scoresMessage.toString()));
    }

private static boolean gameStarted = false; // Prevent multiple game starts
private static Timer gameStartTimer = null; // Timer reference to control the game

public static synchronized void startGame() {
    if (gameStarted || playingClients.size() < 2) {
        return; // Do nothing if the game already started or not enough players
    }

    System.out.println("Waiting for players to join. Timer started.");
    gameStarted = true; // Prevent multiple timers from being created

    // Create a new timer for the 30-second countdown
    gameStartTimer = new Timer();
    gameStartTimer.schedule(new TimerTask() {
        @Override
        public void run() {
            synchronized (Server.class) {
                if (playingClients.size() >= 2) {
                    System.out.println("Timer ended. Starting the game with " + playingClients.size() + " players.");
                    broadcastMessage("GAME_START"); // Notify all players that the game is starting
                    broadcastQuestion(); // Send the first question
                    startGameDurationTimer(); // Start the 2-minute game duration timer
                } else {
                    System.out.println("Not enough players to start the game after 30 seconds.");
                    broadcastMessage("NOT_ENOUGH_PLAYERS: Waiting for more players to join.");
                    resetGameState(); // Reset the game state
                }
            }
        }
    }, 30000); // Wait for 30 seconds
}

public static synchronized void endGame() {
    stopTimers(); // Stop any running timers

    StringBuilder winnerMessage = new StringBuilder("GAME_OVER: ");
    int maxScore = playerScores.values().stream().max(Integer::compare).orElse(0);

    if (maxScore == 0) {
        // No winner
        winnerMessage.append("No winner this time. Everyone scored 0.");
    } else {
        // Determine winners
        List<String> winners = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : playerScores.entrySet()) {
            if (entry.getValue() == maxScore) {
                winners.add(entry.getKey());
            }
        }

        if (winners.size() == 1) {
            winnerMessage.append("Winner: ").append(winners.get(0)).append(" with score: ").append(maxScore);
        } else {
            winnerMessage.append("Winners: ").append(String.join(", ", winners)).append(" with score: ").append(maxScore);
        }
    }

    System.out.println(winnerMessage.toString());

    // Broadcast the game-over message to all clients
    clients.forEach(client -> client.sendMessage(winnerMessage.toString()));

    // Reset game state for a new round
    resetGameState();
}

  public static synchronized void broadcastConnectedPlayers() {
    StringBuilder connectedPlayers = new StringBuilder("Connected users: ");
    for (ClientHandler client : clients) {
        if (client.getUsername() != null) { // Avoid null usernames
            connectedPlayers.append(client.getUsername()).append(",");
        }
    }

    if (connectedPlayers.length() > 17) { // Remove trailing comma
        connectedPlayers.setLength(connectedPlayers.length() - 1);
    }

    // Broadcast the updated list to all clients
    for (ClientHandler client : clients) {
        client.sendMessage(connectedPlayers.toString());
    }
}
public static synchronized void broadcastPlayingPlayers() {
    StringBuilder playingList = new StringBuilder("PLAYING:");
    for (String player : playingClients) {
        playingList.append(player).append(",");
    }

    if (playingList.length() > 8) { // Remove trailing comma
        playingList.setLength(playingList.length() - 1);
    }

    // Broadcast the updated playing list to all connected clients
    clients.forEach(client -> client.sendMessage(playingList.toString()));
}

    private static void startQuestionTimer() {
        final int timeLimit = 25; // Time limit for each question (seconds)
        scheduler.schedule(() -> {
            synchronized (Server.class) {
                System.out.println("Time's up for the current question!");
                currentQuestionIndex++;
                if (currentQuestionIndex < questions.length) {
                    broadcastQuestion();
                } else {
                    endGame();
                }
            }
        }, timeLimit, TimeUnit.SECONDS);
    }

    public static void stopTimers() {
        scheduler.shutdownNow();
        System.out.println("All timers stopped.");
    }


public static synchronized void broadcastMessage(String message) {
    for (ClientHandler client : clients) {
        client.sendMessage(message); // Use the `sendMessage` method in ClientHandler
    }
}
private static synchronized void resetGameState() {
    gameStarted = false; // Reset the gameStarted flag
    currentQuestionIndex = 0; // Reset the question index
    playingClients.clear(); // Clear the waiting list
    playerScores.clear(); // Reset scores
    System.out.println("Game state reset. Ready for a new round.");
}
private static void startGameDurationTimer() {
    Timer gameDurationTimer = new Timer();
    gameDurationTimer.schedule(new TimerTask() {
        @Override
        public void run() {
            synchronized (Server.class) {
                System.out.println("Game duration ended. Checking for winners...");
                endGame(); // End the game after 2 minutes
            }
        }
    }, 120000); // 2 minutes (120,000 milliseconds)
}

}
