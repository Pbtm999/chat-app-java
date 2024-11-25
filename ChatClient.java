import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui
    private String serverIp;
    private int port;
    private SocketChannel sc;
    private Charset charset = Charset.forName("UTF-8");

    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                chatArea.append(message + "\n");
            }
        });
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                    ex.printStackTrace();
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Se for necessário adicionar código de inicialização ao
        // construtor, deve ser colocado aqui
        serverIp = server;
        this.port = port;

        sc = SocketChannel.open();
        sc.configureBlocking(false);
        sc.connect(new InetSocketAddress(serverIp, this.port));
    }

    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException {
        if (sc.finishConnect()) {
            ByteBuffer buffer = charset.encode(message);
            sc.write(buffer);
            if (!message.startsWith("/"))
                printMessage("You: " + message); // Display the message in the client's UI
        } else {
            System.err.println("Not connected yet.");
        }
    }

    // Método principal do objecto
    public void run() throws IOException {
        while (!sc.finishConnect()) {
            // Wait until the connection is established
        }
        System.out.println("Connected to the server.");

        // Start a new thread to read messages from the server
        new Thread(new Runnable() {
            public void run() {
                try {
                    readMessages();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    // Method to read messages from the server
    private void readMessages() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(16384);
        while (true) {
            buffer.clear();
            int bytesRead = sc.read(buffer);
            if (bytesRead > 0) {
                buffer.flip();
                String message = charset.decode(buffer).toString();
                printMessage(message);
            }
        }
    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}