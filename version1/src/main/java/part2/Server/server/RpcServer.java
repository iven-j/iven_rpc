package part2.Server.server;

/**
 * @author iven
 * @version 1.0
 * @create 2024/7/12 11:26
 */
public interface RpcServer {
    void start(int port);
    void stop();
}
