package part1.Client;

import part1.common.Message.RpcRequest;
import part1.common.Message.RpcResponse;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class IOClient {
    /**
     * 发送请求到服务端并返回响应
     * @param host 服务端主机地址
     * @param port 服务端端口号
     * @param request 要发送的RpcRequest对象
     * @return 从服务端返回的RpcResponse对象
     * @throws IOException 当发生输入输出异常时
     * @throws ClassNotFoundException 当找不到相应的类时
     */
    //这里负责底层与服务端的通信，发送request，返回response
    public static RpcResponse sendRequest(String host, int port, RpcRequest request){
        try {
            Socket socket=new Socket(host, port);
            ObjectOutputStream oos=new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois=new ObjectInputStream(socket.getInputStream());

            oos.writeObject(request);
            oos.flush();

            RpcResponse response=(RpcResponse) ois.readObject();
            return response;
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}
