package part1.common.serializer.mySerializer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import part1.common.Message.RpcRequest;
import part1.common.Message.RpcResponse;

/**
 * @author iven
 * @version 1.0
 * @create 2024/7/15 22:31
 */
public class JsonSerializer implements Serializer {

    /**
 * 将对象序列化为字节数组的方法    。
 * 该方法使用JSON格式将传入的对象转换为字节数组    。
 *
 * @param obj 需要序列化的对象，预期为任意类型的对象
 * @return byte[] 返回序列化后的字节数组，其中包含了传入对象的JSON表示形式
 */

    @Override
    public byte[] serialize(Object obj) {
        byte[] bytes = JSONObject.toJSONBytes(obj);
        return bytes;
    }
    /**
 * 根据消息类型反序列化字节数组为相应的请求或响应对象    。
 * 此方法处理两种消息类型：请求（RpcRequest）和响应（RpcResponse）    。
 *
 * @param bytes 要反序列化的字节数组
 * @param messageType 消息类型，用于决定如何反序列化字节数组
 * @return Object 反序列化后的请求或响应对象
 *
 * @throws RuntimeException 当不支持的消息类型被传递时抛出
 */

    @Override
    public Object deserialize(byte[] bytes, int messageType) {
        Object obj = null;
        // 传输的消息分为request与response
        switch (messageType){
            case 0:
                RpcRequest request = JSON.parseObject(bytes, RpcRequest.class);
                Object[] objects = new Object[request.getParams().length];
                // 把json字串转化成对应的对象， fastjson可以读出基本数据类型，不用转化
                // 对转换后的request中的params属性逐个进行类型判断
                for(int i = 0; i < objects.length; i++){
                    Class<?> paramsType = request.getParamsType()[i];
                    //判断每个对象类型是否和paramsTypes中的一致
                    if (!paramsType.isAssignableFrom(request.getParams()[i].getClass())){
                        //如果不一致，就行进行类型转换
                        objects[i] = JSONObject.toJavaObject((JSONObject) request.getParams()[i],request.getParamsType()[i]);
                    }else{
                        //如果一致就直接赋给objects[i]
                        objects[i] = request.getParams()[i];
                    }
                }
                request.setParams(objects);
                obj = request;
                break;
            case 1:
                RpcResponse response = JSON.parseObject(bytes, RpcResponse.class);
                Class<?> dataType = response.getDataType();
                //判断转化后的response对象中的data的类型是否正确
                if(! dataType.isAssignableFrom(response.getData().getClass())){
                    response.setData(JSONObject.toJavaObject((JSONObject) response.getData(),dataType));
                }
                obj = response;
                break;
            default:
                System.out.println("暂时不支持此种消息");
                throw new RuntimeException();
        }
        return obj;
    }

    //1 代表json序列化方式
    @Override
    public int getType() {
        return 1;
    }
}
