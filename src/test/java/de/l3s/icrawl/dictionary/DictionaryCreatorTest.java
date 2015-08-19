package de.l3s.icrawl.dictionary;

import org.junit.Test;

import static de.l3s.icrawl.dictionary.DictionaryCreator.idf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class DictionaryCreatorTest {

    private static final double EPSILON = .001;

    @Test
    public void testIdf() throws Exception {
        assertEquals(0.0, idf(10, 10), EPSILON);
        assertThat(idf(1, 10), is(greaterThan(1.0)));
        assertEquals(idf(0, 10), idf(1, 10), EPSILON);
        assertEquals(idf(-1, 10), idf(1, 10), EPSILON);
    }
}
