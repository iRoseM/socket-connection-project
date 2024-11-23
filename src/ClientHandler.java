import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private String username;
    private List<ClientHandler> clients;

    public ClientHandler(Socket clientSocket, List<ClientHandler> clients) throws IOException {
        this.clientSocket = clientSocket;
        this.clients = clients;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.username = in.readLine(); // Read username from the client during connection
        Server.playerScores.put(username, 0); // Initialize player's score on connection
    }

    @Override
    public void run() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                if (message.startsWith("PLAY:")) {
                    handlePlayRequest();
                } else if (message.equalsIgnoreCase("DISCONNECT")) {
                    disconnectClient();
                    break;
                } else if (message.startsWith("ANSWER:")) {
                    String playerAnswer = message.substring(7).trim();
                    handleAnswer(playerAnswer);
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client " + username + ": " + e.getMessage());
        } finally {
            disconnectClient();
        }
    }
    /**
     * Handles the client's request to join the playing list.
     */

    /**
     * Handles the client's submitted answer.
     */
    private void handleAnswer(String playerAnswer) {
        synchronized (Server.class) {
            if (Server.currentQuestionIndex < Server.questions.length) {
                if (playerAnswer.equalsIgnoreCase(Server.answers[Server.currentQuestionIndex])) {
                    int currentScore = Server.playerScores.getOrDefault(username, 0);
                    Server.playerScores.put(username, currentScore + 10);
                    sendMessage("CORRECT_ANSWER");
                } else {
                    sendMessage("WRONG_ANSWER");
                }

                Server.broadcastScores();
                Server.currentQuestionIndex++;

                if (Server.currentQuestionIndex < Server.questions.length) {
                    Server.broadcastQuestion();
                } else {
                    Server.endGame();
                }
            } else {
                Server.endGame();
            }
        }
    }
private void handlePlayRequest() {
    synchronized (Server.playingClients) {
        if (Server.playingClients.size() < 3 && !Server.playingClients.contains(username)) {
            Server.playingClients.add(username); // Add player to the waiting list
            Server.broadcastPlayingPlayers(); // Update the waiting list for all clients
            if (Server.playingClients.size() == 2) {
                Server.startGame(); // Start the timer if 2 players have joined
            }
        } else if (Server.playingClients.size() >= 3) {
            sendMessage("ROOM_FULL"); // Notify the client that the room is full
        }
    }
}

    /**
     * Disconnects the client and cleans up resources.
     */
    private void disconnectClient() {
    try {
        // Remove the player from the waiting list
        synchronized (Server.playingClients) {
            if (Server.playingClients.contains(username)) {
                Server.playingClients.remove(username);
                Server.broadcastPlayingPlayers(); // Broadcast updated waiting list
            }
        }

        // Remove the player from the connected clients list
        synchronized (Server.clients) {
            Server.clients.remove(this);
            Server.broadcastConnectedPlayers(); // Broadcast updated connected players
        }

        // Close the client socket
        if (clientSocket != null) {
            clientSocket.close();
        }

        System.out.println("Client " + username + " disconnected.");
    } catch (IOException e) {
        System.err.println("Error disconnecting client " + username + ": " + e.getMessage());
    }
}


    /**
     * Sends a message to the client.
     *
     * @param message The message to send.
     */
 
    public void sendMessage(String message) {
    if (out != null) {
        out.println(message);
        out.flush();
    }
}

    /**
     * Returns the username of the client.
     *
     * @return The client's username.
     */
    public String getUsername() {
        return username;
    }
}
