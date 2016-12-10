 package ubicrypt.core.remote;

import org.slf4j.Logger;

import java.util.Optional;

import ubicrypt.core.FileProvenience;
import ubicrypt.core.IRepository;
import ubicrypt.core.dto.RemoteConfig;
import ubicrypt.core.dto.RemoteFile;
import ubicrypt.core.dto.UbiFile;
import ubicrypt.core.dto.VClock;
import ubicrypt.core.provider.UbiProvider;

import static org.slf4j.LoggerFactory.getLogger;

 public class OnErrorRemote extends OnUpdateRemote {
     private static final Logger log = getLogger(OnErrorRemote.class);
    public OnErrorRemote(UbiProvider provider, IRepository repository) {
        super(provider, repository);
    }

    @Override
    public boolean test(FileProvenience fileProvenience, RemoteConfig remoteConfig) {
        UbiFile file = fileProvenience.getFile();
        Optional<RemoteFile> rfile = remoteConfig.getRemoteFiles().stream().filter(file1 -> file1.equals(file)).findFirst();
        log.trace("path:{}, local v:{}, remote v:{}, comparison:{}, test:{}",file.getPath(), file.getVclock(),
                rfile.get().getVclock(), file.compare(rfile.get()), rfile.isPresent()?rfile.get().isError():false);
        if (!rfile.isPresent()) {
            return false;
        }
        return rfile.get().isError();
    }

}
