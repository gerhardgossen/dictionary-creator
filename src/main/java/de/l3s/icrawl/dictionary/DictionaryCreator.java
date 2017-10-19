package de.l3s.icrawl.dictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.io.Files;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public class DictionaryCreator {
    private static final String FILENAME_PATTERN = ".*-\\w+\\.gz$";

    private static class DictionaryEntry {
        private final static Splitter TOKENIZER = Splitter.on('\t').limit(4);
        final String word;
        final int year;
        final long volumeCount;

        DictionaryEntry(String word, int year, long matchCount, long volumeCount) {
            this.word = word;
            this.year = year;
            this.volumeCount = volumeCount;
        }

        public String getWord() {
            return word;
        }

        public long getVolumeCount() {
            return volumeCount;
        }

        static DictionaryEntry parse(String line) {
            List<String> parts = TOKENIZER.splitToList(line);
            if (parts.size() != 4) {
                throw new IllegalArgumentException("Not a valid entry: " + line);
            }
            return new DictionaryEntry(parts.get(0), Integer.parseInt(parts.get(1)),
                Long.parseLong(parts.get(2)), Long.parseLong(parts.get(3)));
        }

        @Override
        public String toString() {
            return String.format(Locale.ROOT, "%s@%d: %d", word, year, volumeCount);
        }
    }

    private static class Counted {
        private final String key;
        private final long count;

        Counted(String key, long count) {
            this.key = key;
            this.count = count;
        }

        public String getKey() {
            return key;
        }

        public long getCount() {
            return count;
        }

        public Counted withKey(String newKey) {
            return Objects.equals(key, newKey) ? this : new Counted(newKey, count);
        }
    }

    private static final int BUFFER_SIZE = 8048;

    @Option(name = "-y", usage = "Number of years to include", aliases = "--years")
    private int numYears = 25;

    @Option(name = "-k", usage = "Number of words to keep (sorted by idf)", aliases = "--words")
    private int numWords = 100_000;

    @Argument(index = 1, required = true, usage = "output file", metaVar = "OUT")
    private File outputFile;

    @Argument(index = 0, required = true, usage = "directory containing data files", metaVar = "INPUT_DIR")
    private File unigramsDir;

    private int firstYear;

    private long totalVolumes;


    public static void main(String[] args) throws IOException {

        DictionaryCreator creator = new DictionaryCreator();
        // prevent Eclipse from making arguments final
        creator.numYears = creator.numYears;
        creator.numWords = creator.numWords;
        CmdLineParser parser = new CmdLineParser(creator);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println("usage: java " + DictionaryCreator.class.getName() + " [options] INPUT_DIR OUT");
            parser.printUsage(System.err);
            System.exit(1);
        }
        creator.run();
    }

    public void run() throws IOException {
        readTotals();
        System.out.println("First year: " + firstYear);
        File[] letterFiles = unigramsDir.listFiles((dir, name) -> name.matches(FILENAME_PATTERN));
        Arrays.sort(letterFiles, (a, b) -> a.getName().compareTo(b.getName()));
        System.out.println("Using files: " + stream(letterFiles).map(f -> f.getName()).collect(joining(",", "[", "]")));
        Map<String, Long> globalDictionary = new HashMap<>(7_500_000);
        for (File letterFile : letterFiles) {
            try (BufferedReader reader = openGZipFileReader(letterFile, UTF_8, BUFFER_SIZE)) {
                Map<String, Long> wordWithPosCounts = reader.lines()
                    .map(DictionaryEntry::parse)
                    .filter(e -> e.year >= firstYear)
                    .collect(toMap(DictionaryEntry::getWord, DictionaryEntry::getVolumeCount, (a, b) -> a + b));
                Map<String, Long> dictionary = wordWithPosCounts.entrySet()
                    .stream()
                    .map(e -> new Counted(e.getKey(), e.getValue()))
                    .map(c -> c.withKey(cleanPos(c.key)))
                    .collect(toMap(Counted::getKey, Counted::getCount, (a, b) -> Math.max(a, b)));
                globalDictionary.putAll(dictionary);
                System.out.printf("%s -> %d, total=%d%n", letterFile.getName(), dictionary.size(),
                    globalDictionary.size());
            }
        }
        try (PrintWriter writer = new PrintWriter(Files.newWriter(outputFile, UTF_8))) {
            globalDictionary.entrySet()
                .stream()
                .sorted((a, b) -> -Long.compare(a.getValue(), b.getValue()))
                .limit(numWords)
                .forEach(e -> writer.printf(Locale.ROOT, "%s\t%15.13f%n", e.getKey(), idf(e.getValue(), totalVolumes)));
        }
    }

    private static String cleanPos(String s) {
        int position = s.indexOf("_");
        return position < 0 ? s : s.substring(0, position);
    }

    private static BufferedReader openGZipFileReader(File letterFile, Charset cs, int bufferSize) throws IOException {
        GZIPInputStream is = new GZIPInputStream(new FileInputStream(letterFile), bufferSize);
        return new BufferedReader(new InputStreamReader(is, cs), bufferSize);
    }

    private void readTotals() throws IOException {
        File[] totalCountsFiles = unigramsDir.listFiles((dir, name) -> name.indexOf("totalcounts") >= 0);
        int[] years = new int[numYears];
        long[] counts = new long[numYears];
        int idx = 0;
        try (BufferedReader reader = Files.newReader(totalCountsFiles[0], US_ASCII);
                Scanner scanner = new Scanner(reader)) {
            scanner.useDelimiter("\\t|,").useLocale(Locale.ENGLISH);
            // skip first blank record
            scanner.next();
            while (scanner.hasNext()) {
                int year = scanner.nextInt();
                // tokenCount
                scanner.nextLong();
                // page_count
                scanner.nextLong();
                long volumeCount = scanner.nextLong();
                years[idx] = year;
                counts[idx] = volumeCount;
                idx = (idx + 1) % numYears;
            }
        }
        firstYear = years[idx];
        totalVolumes = stream(counts).sum();
    }

    @VisibleForTesting
    static double idf(long volumeCount, long totalDocuments) {
        return Math.log(totalDocuments / (double) (volumeCount > 0 ? volumeCount : 1));
    }

}
