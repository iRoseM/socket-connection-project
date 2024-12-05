import java.io.*;
import java.net.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

public class ServerConnection implements Runnable {
    private Socket clientSocket;
    private Client clientFrame;
    private BufferedReader in;

    public ServerConnection(Socket clientSocket, Client clientFrame) throws IOException {
        this.clientSocket = clientSocket;
        this.clientFrame = clientFrame;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    }

    @Override
    public void run() {
        try {
            String serverResponse;
            while ((serverResponse = in.readLine()) != null) {
                System.out.println("Received from server: " + serverResponse);
              

                // Handle different server messages
                  if (serverResponse.startsWith("Connected users: ")) {
                clientFrame.updateConnectedPlayers(serverResponse.substring(17));
                } else if (serverResponse.startsWith("PLAYING:")) {
                clientFrame.updatePlayingClients(serverResponse.substring(8));
                } else if (serverResponse.startsWith("SCORES:")) {
                    clientFrame.updateScores(serverResponse.substring(7));
                } else if (serverResponse.equals("ROOM_FULL")) {
                    clientFrame.displayRoomFullMessage();
                } else if (serverResponse.startsWith("PLAYERS:")) {
                    clientFrame.updateConnectedPlayers(serverResponse.substring(8));
                } else if (serverResponse.equals("WRONG_ANSWER")) {
                    SwingUtilities.invokeLater(() -> clientFrame.showWrongAnswer());
                }else if (serverResponse.equals("GAME_START")) {
                    clientFrame.prepareForGameStart(); // Notify the client that the game is starting
                } else if (serverResponse.startsWith("QUESTION:")) {
                    clientFrame.displayQuestion(serverResponse.substring(9));
                } else if (serverResponse.startsWith("GAME_OVER:")) {
                    clientFrame.displayGameOver(serverResponse.substring(10));
                }else if (serverResponse.equals("KICKED")) {
                    clientFrame.displaykickedMessage();
                }
            
        }} catch (IOException e) {
            e.printStackTrace();
        } catch (Exception ex) {
            Logger.getLogger(ServerConnection.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

