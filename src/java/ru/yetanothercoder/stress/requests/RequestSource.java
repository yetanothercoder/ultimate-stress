package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/en
 */
public interface RequestSource {
    /**
     * Generate request contents
     *
     * @return request contents
     */
    ChannelBuffer next();
}
