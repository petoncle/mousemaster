package mousemaster;

import java.time.Duration;

public record AnimationConfiguration(Duration duration, Integer repeatCount,
                                     AnimationDirection direction) {

}
