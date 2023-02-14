public interface Server {
  void start() throws Exception;

  void close() throws Exception;

  String getAddress() throws Exception;
}
