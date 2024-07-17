package part1.common.serializer.myCode;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import part1.common.Message.MessageType;
import part1.common.serializer.mySerializer.Serializer;

import java.awt.*;
import java.util.List;

/**
 * @author iven
 * @version 1.0
 * @create 2024/7/15 22:24
 * 按照自定义的消息格式解码数据
 * 1. `readShort()`方法从`ByteBuf`中读取两个字节，并将它们组合为一个短整型（`short`）值，这里用于读取消息类型。消息类型用于区分不同的消息，如请求和响应。
 * 2. 判断消息类型是否为支持的类型（在这里是`REQUEST`或`RESPONSE`）。如果不是支持的类型，则打印一条消息表示暂不支持此种数据，并返回。
 * 3. 再次使用`readShort()`方法从`ByteBuf`中读取两个字节，获取序列化器的类型。序列化器负责将字节流转换为特定类型的对象。
 * 4. 通过序列化器类型，使用`Serializer.getSerializerByCode(serializerType)`方法获取对应的序列化器实例。如果没有找到对应的序列化器，抛出一个运行时异常。
 * 5. 使用`readInt()`方法从`ByteBuf`中读取四个字节，转换为整型（`int`）值，表示后续序列化数组的长度。
 * 6. 根据读取到的长度，创建一个字节数组`bytes`，并使用`readBytes(bytes)`方法从`ByteBuf`中读取指定长度的字节数据到数组中。
 * 7. 使用获取到的序列化器调用`deserialize`方法，将字节数组和消息类型作为参数，反序列化出一个Java对象。
 * 8. 将反序列化得到的对象添加到`out`列表中，`out`作为解码方法的输出，存储解码后的消息对象，供后续的处理器使用。
 */
public class MyDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {
        //1.读取消息类型
        short messageType = in.readShort();
        // 现在还只支持request与response请求
        if(messageType != MessageType.REQUEST.getCode() &&
                messageType != MessageType.RESPONSE.getCode()){
            System.out.println("暂不支持此种数据");
            return;
        }
        //2.读取序列化的方式&类型
        short serializerType = in.readShort();
        Serializer serializer = Serializer.getSerializerByCode(serializerType);
        if(serializer == null)
            throw new RuntimeException("不存在对应的序列化器");
        //3.读取序列化数组长度
        int length = in.readInt();
        //4.读取序列化数组
        byte[] bytes=new byte[length];
        in.readBytes(bytes);
        Object deserialize= serializer.deserialize(bytes, messageType);
        out.add(deserialize);
    }
}
