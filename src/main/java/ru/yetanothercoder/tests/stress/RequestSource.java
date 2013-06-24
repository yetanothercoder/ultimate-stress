package ru.yetanothercoder.tests.stress;

import org.jboss.netty.buffer.ChannelBuffer;

/**
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/en
 */
public interface RequestSource {
    /**
     * Generate request contents
     * !!! if you use http request - check that you have 2 empty lines at the end (\n\n)
     *
     * @return request contents
     */
    ChannelBuffer next();
}
