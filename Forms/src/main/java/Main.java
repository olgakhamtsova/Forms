import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Optional;

public class Main {
    public static final String GET = "GET";
    public static final String POST = "POST";
    static final int PORT = 8080;
    static ExecutorService executeIt = Executors.newFixedThreadPool(64);
    public static void main(String[] args) {
        final var allowedMethods = List.of(GET, POST);
        System.out.println("Hello World!");
        try (ServerSocket s = new ServerSocket(PORT)) {
            System.out.println("Server Started");
            try {
                while (true) {
                    Socket socket = s.accept();
                    final var in = new BufferedInputStream(socket.getInputStream());
                    final var out = new BufferedOutputStream(socket.getOutputStream());
                    // лимит на request line + заголовки
                    final var limit = 4096;

                    in.mark(limit);
                    final var buffer = new byte[limit];
                    final var read = in.read(buffer);

                    // ищем request line
                    final var requestLineDelimiter = new byte[]{'\r', '\n'};
                    final var requestLineEnd = indexOf(buffer, requestLineDelimiter, 0, read);
                    if (requestLineEnd == -1) {
                        badRequest(out);
                        continue;
                    }

                    // читаем request line
                    final var requestLine = new String(Arrays.copyOf(buffer, requestLineEnd)).split(" ");
                    if (requestLine.length != 3) {
                        badRequest(out);
                        continue;
                    }

                    final var path = requestLine[1];
                    if (!path.startsWith("/")) {
                        badRequest(out);
                        continue;
                    }
                    System.out.println(path);

                    // ищем заголовки
                    final var headersDelimiter = new byte[]{'\r', '\n', '\r', '\n'};
                    final var headersStart = requestLineEnd + requestLineDelimiter.length;
                    final var headersEnd = indexOf(buffer, headersDelimiter, headersStart, read);
                    if (headersEnd == -1) {
                        badRequest(out);
                        continue;
                    }
                    // отматываем на начало буфера
                    in.reset();
                    // пропускаем requestLine
                    in.skip(headersStart);

                    final var headersBytes = in.readNBytes(headersEnd - headersStart);
                    final var headers = Arrays.asList(new String(headersBytes).split("\r\n"));
                    System.out.println(headers);

                    Handler handler = new Handler(){
                        @Override
                        public void handle(Request request, BufferedOutputStream responseStream) throws IOException {
                            //int question = request.indexOf('?');

                        }


                    };

                    try {
                        executeIt.execute(new Server(socket));
                    } catch (IOException e) {
                        socket.close();
                    }
                }
            } finally {
                s.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private static Optional<String> extractHeader(List<String> headers, String header) {
        return headers.stream()
                .filter(o -> o.startsWith(header))
                .map(o -> o.substring(o.indexOf(" ")))
                .map(String::trim)
                .findFirst();
    }

    private static void badRequest(BufferedOutputStream out) throws IOException {
        out.write((
                "HTTP/1.1 400 Bad Request\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }

    // from google guava with modifications
    private static int indexOf(byte[] array, byte[] target, int start, int max) {
        outer:
        for (int i = start; i < max - target.length + 1; i++) {
            for (int j = 0; j < target.length; j++) {
                if (array[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
