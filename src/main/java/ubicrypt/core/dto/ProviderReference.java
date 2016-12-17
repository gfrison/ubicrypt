package ubicrypt.core.dto;

public class RemoteProvider {
  private RemoteFile confFile =
    new RemoteFile() {
      {
        setKey(
          new Key() {
            {
              setType(UbiFile.KeyType.pgp);
            }
          });
      }
    };
  private RemoteFile lockFile =
    new RemoteFile() {
      {
        setKey(
          new Key() {
            {
              setType(UbiFile.KeyType.pgp);
            }
          });
      }
    };

}
