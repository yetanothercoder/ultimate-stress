package ru.yetanothercoder.stress.requests;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 * Request source or request iterator
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/en
 */
public interface RequestSource {
    /**
     * Generate request contents
     * It's called on each request, so must be FAST!
     *
     * @return request contents
     */
    ChannelBuffer next();
}
