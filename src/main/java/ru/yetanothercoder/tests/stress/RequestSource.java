package ru.yetanothercoder.tests.stress;

/**
 *
 * @author Mikhail Baturov, http://www.yetanothercoder.ru/search/label/en
 */
public interface RequestSource<R> {
    /**
     * Generate request contents
     * !!! if you use http request - check that you have 2 empty lines at the end (\n\n)
     *
     * @return request contents
     */
    R next();
}
