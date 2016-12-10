package ubicrypt.core;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.slf4j.LoggerFactory.getLogger;

@Configuration
public class PathConf {
    private static final Logger log = getLogger(PathConf.class);

    @Bean
    public Path basePath(@Value("${home:@null}") final String home) {
        final Path ret = StringUtils.isEmpty(home) ? Paths.get(System.getProperty("user.home")) : Paths.get(home);
        log.info("home folder:{}", ret);
        return ret;
    }

}
