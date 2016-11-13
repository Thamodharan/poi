/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.apache.poi.hwpf.usermodel;

import junit.framework.TestCase;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.poi.POIDataSamples;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.HWPFOldDocument;
import org.apache.poi.hwpf.HWPFTestDataSamples;
import org.apache.poi.hwpf.converter.AbstractWordUtils;
import org.apache.poi.hwpf.converter.WordToTextConverter;
import org.apache.poi.hwpf.extractor.Word6Extractor;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hwpf.model.FieldsDocumentPart;
import org.apache.poi.hwpf.model.FileInformationBlock;
import org.apache.poi.hwpf.model.PlexOfField;
import org.apache.poi.hwpf.model.SubdocumentType;
import org.apache.poi.hwpf.model.io.HWPFOutputStream;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.POILogFactory;
import org.apache.poi.util.POILogger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Test different problems reported in the Apache Bugzilla
 *  against HWPF
 */
public class TestBugs extends TestCase
{
    private static final POILogger logger = POILogFactory
            .getLogger(TestBugs.class);

    public static void assertEqualsIgnoreNewline(String expected, String actual )
    {
        String newExpected = expected.replaceAll("\r\n", "\n" )
                .replaceAll("\r", "\n").trim();
        String newActual = actual.replaceAll("\r\n", "\n" )
                .replaceAll("\r", "\n").trim();
        TestCase.assertEquals(newExpected, newActual);
    }

    private static void assertTableStructures(Range expected, Range actual )
    {
        assertEquals(expected.numParagraphs(), actual.numParagraphs());
        for (int p = 0; p < expected.numParagraphs(); p++ )
        {
            Paragraph expParagraph = expected.getParagraph(p);
            Paragraph actParagraph = actual.getParagraph(p);

            assertEqualsIgnoreNewline(expParagraph.text(), actParagraph.text());
            assertEquals("Diffent isInTable flags for paragraphs #" + p
                    + " -- " + expParagraph + " -- " + actParagraph + ".",
                    expParagraph.isInTable(), actParagraph.isInTable());
            assertEquals(expParagraph.isTableRowEnd(),
                    actParagraph.isTableRowEnd());

            if (expParagraph.isInTable() && actParagraph.isInTable() )
            {
                Table expTable, actTable;
                try
                {
                    expTable = expected.getTable(expParagraph);
                    actTable = actual.getTable(actParagraph);
                }
                catch (Exception exc )
                {
                    continue;
                }

                assertEquals(expTable.numRows(), actTable.numRows());
                assertEquals(expTable.numParagraphs(),
                        actTable.numParagraphs());
            }
        }
    }

    private static void fixed(String bugzillaId )
    {
        throw new Error(
                "Bug "
                        + bugzillaId
                        + " seems to be fixed. "
                        + "Please resolve the issue in Bugzilla and remove fail() from the test");
    }

    private String getText(String samplefile) throws IOException {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile(samplefile);
        WordExtractor extractor = new WordExtractor(doc);
        try {
            return extractor.getText();
        } finally {
            extractor.close();
        }
    }
    
    private String getTextOldFile(String samplefile) throws IOException {
        HWPFOldDocument doc = HWPFTestDataSamples.openOldSampleFile(samplefile);
        Word6Extractor extractor = new Word6Extractor(doc);
        try {
            return extractor.getText();
        } finally {
            extractor.close();
        }
    }

    /**
     * Bug 33519 - HWPF fails to read a file
     */
    public void test33519() throws IOException
    {
        assertNotNull(getText("Bug33519.doc"));
    }

    /**
     * Bug 34898 - WordExtractor doesn't read the whole string from the file
     */
    public void test34898() throws IOException
    {
        assertEqualsIgnoreNewline("\u30c7\u30a3\u30ec\u30af\u30c8\u30ea", getText("Bug34898.doc").trim());
    }

    /**
     * [RESOLVED INVALID] 41898 - Word 2003 pictures cannot be extracted
     */
    public void test41898()
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug41898.doc");
        List<Picture> pics = doc.getPicturesTable().getAllPictures();

        assertNotNull(pics);
        assertEquals(1, pics.size());

        Picture pic = pics.get(0);
        assertNotNull(pic.suggestFileExtension());
        assertNotNull(pic.suggestFullFileName());

        assertNotNull(pic.getContent());
        assertNotNull(pic.getRawContent());

        /*
         * This is a file with empty EMF image, but present Office Drawing
         * --sergey
         */
        final Collection<OfficeDrawing> officeDrawings = doc
                .getOfficeDrawingsMain().getOfficeDrawings();
        assertNotNull(officeDrawings);
        assertEquals(1, officeDrawings.size());

        OfficeDrawing officeDrawing = officeDrawings.iterator().next();
        assertNotNull(officeDrawing);
        assertEquals(1044, officeDrawing.getShapeId());
    }

    /**
     * Bug 44331 - HWPFDocument.write destroys fields
     */
    @SuppressWarnings("deprecation")
    public void test44431() throws IOException
    {
        HWPFDocument doc1 = HWPFTestDataSamples.openSampleFile("Bug44431.doc");

        WordExtractor extractor1 = new WordExtractor(doc1);
        try {
            HWPFDocument doc2 = HWPFTestDataSamples.writeOutAndReadBack(doc1);
            
            WordExtractor extractor2 = new WordExtractor(doc2);
            try {
                assertEqualsIgnoreNewline(extractor1.getFooterText(), extractor2.getFooterText());
                assertEqualsIgnoreNewline(extractor1.getHeaderText(), extractor2.getHeaderText());
                assertEqualsIgnoreNewline(Arrays.toString(extractor1.getParagraphText() ),
                        Arrays.toString(extractor2.getParagraphText()));
        
                assertEqualsIgnoreNewline(extractor1.getText(), extractor2.getText());
            } finally {
                extractor2.close();
            }
        } finally {
            extractor1.close();
        }
    }

    /**
     * Bug 44331 - HWPFDocument.write destroys fields
     */
    public void test44431_2() throws IOException
    {
        assertEqualsIgnoreNewline("File name=FieldsTest.doc\n" + 
        		"\n" + 
        		"\n" + 
        		"STYLEREF test\n" + 
        		"\n" + 
        		"\n" + 
        		"\n" + 
        		"TEST TABLE OF CONTENTS\n" + 
        		"\n" + 
        		"Heading paragraph in next page\t2\n" + 
        		"Another heading paragraph in further page\t3\n" + 
        		"Another heading paragraph in further page\t3\n" + 
        		"\n" + 
        		"\n" + 
        		"Heading paragraph in next page\n" + 
        		"Another heading paragraph in further page\n" + 
        		"\n" + 
        		"\n" + 
        		"\n" + 
        		"Page 3 of 3", getText("Bug44431.doc"));
    }

    /**
     * Bug 45473 - HWPF cannot read file after save
     */
    public void test45473() throws IOException
    {
        // Fetch the current text
        HWPFDocument doc1 = HWPFTestDataSamples.openSampleFile("Bug45473.doc");
        WordExtractor wordExtractor = new WordExtractor(doc1);
        final String text1;
        try {
            text1 = wordExtractor.getText().trim();
        } finally {
            wordExtractor.close();
        }

        // Re-load, then re-save and re-check
        doc1 = HWPFTestDataSamples.openSampleFile("Bug45473.doc");
        HWPFDocument doc2 = HWPFTestDataSamples.writeOutAndReadBack(doc1);
        WordExtractor wordExtractor2 = new WordExtractor(doc2);
        final String text2;
        try {
            text2 = wordExtractor2.getText().trim();
        } finally {
            wordExtractor2.close();
        }

        // the text in the saved document has some differences in line
        // separators but we tolerate that
        assertEqualsIgnoreNewline(text1.replaceAll("\n", "" ), text2.replaceAll("\n", ""));
    }

    /**
     * Bug 46220 - images are not properly extracted
     */
    public void test46220()
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug46220.doc");
        // reference checksums as in Bugzilla
        String[] md5 = { "851be142bce6d01848e730cb6903f39e",
                "7fc6d8fb58b09ababd036d10a0e8c039",
                "a7dc644c40bc2fbf17b2b62d07f99248",
                "72d07b8db5fad7099d90bc4c304b4666" };
        List<Picture> pics = doc.getPicturesTable().getAllPictures();
        assertEquals(4, pics.size());
        for (int i = 0; i < pics.size(); i++ )
        {
            Picture pic = pics.get(i);
            byte[] data = pic.getRawContent();
            // use Apache Commons Codec utils to compute md5
            assertEqualsIgnoreNewline(md5[i], DigestUtils.md5Hex(data));
        }
    }

    /**
     * [RESOLVED FIXED] Bug 46817 - Regression: Text from some table cells
     * missing
     */
    public void test46817() throws IOException
    {
        String text = getText("Bug46817.doc").trim();

        assertTrue(text.contains("Nazwa wykonawcy"));
        assertTrue(text.contains("kujawsko-pomorskie"));
        assertTrue(text.contains("ekomel@ekomel.com.pl"));
    }

    /**
     * [FAILING] Bug 47286 - Word documents saves in wrong format if source
     * contains form elements
     * 
     */
    @SuppressWarnings("deprecation")
    public void test47286() throws IOException
    {
        // Fetch the current text
        HWPFDocument doc1 = HWPFTestDataSamples.openSampleFile("Bug47286.doc");
        WordExtractor wordExtractor = new WordExtractor(doc1);
        final String text1;
        try {
            text1 = wordExtractor.getText().trim();
        } finally {
            wordExtractor.close();
        }

        // Re-load, then re-save and re-check
        doc1 = HWPFTestDataSamples.openSampleFile("Bug47286.doc");
        HWPFDocument doc2 = HWPFTestDataSamples.writeOutAndReadBack(doc1);
        WordExtractor wordExtractor2 = new WordExtractor(doc2);
        final String text2;
        try {
            text2 = wordExtractor2.getText().trim();
        } finally {
            wordExtractor2.close();
        }

        // the text in the saved document has some differences in line
        // separators but we tolerate that
        assertEqualsIgnoreNewline(text1.replaceAll("\n", "" ), text2.replaceAll("\n", ""));

        assertEquals(doc1.getCharacterTable().getTextRuns().size(), doc2
                .getCharacterTable().getTextRuns().size());

        List<PlexOfField> expectedFields = doc1.getFieldsTables()
                .getFieldsPLCF(FieldsDocumentPart.MAIN);
        List<PlexOfField> actualFields = doc2.getFieldsTables().getFieldsPLCF(
                FieldsDocumentPart.MAIN);
        assertEquals(expectedFields.size(), actualFields.size());

        assertTableStructures(doc1.getRange(), doc2.getRange());
    }

    /**
     * [RESOLVED FIXED] Bug 47287 - StringIndexOutOfBoundsException in
     * CharacterRun.replaceText()
     */
    public void test47287()
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug47287.doc");
        String[] values = { "1-1", "1-2", "1-3", "1-4", "1-5", "1-6", "1-7",
                "1-8", "1-9", "1-10", "1-11", "1-12", "1-13", "1-14", "1-15", };
        int usedVal = 0;
        String PLACEHOLDER = "\u2002\u2002\u2002\u2002\u2002";
        Range r = doc.getRange();
        for (int x = 0; x < r.numSections(); x++ )
        {
            Section s = r.getSection(x);
            for (int y = 0; y < s.numParagraphs(); y++ )
            {
                Paragraph p = s.getParagraph(y);

                for (int z = 0; z < p.numCharacterRuns(); z++ )
                {
                    boolean isFound = false;

                    // character run
                    CharacterRun run = p.getCharacterRun(z);
                    // character run text
                    String text = run.text();
                    String oldText = text;
                    int c = text.indexOf("FORMTEXT ");
                    if (c < 0 )
                    {
                        int k = text.indexOf(PLACEHOLDER);
                        if (k >= 0 )
                        {
                            text = text.substring(0, k ) + values[usedVal]
                                    + text.substring(k + PLACEHOLDER.length());
                            usedVal++;
                            isFound = true;
                        }
                    }
                    else
                    {
                        for (; c >= 0; c = text.indexOf("FORMTEXT ", c
                                + "FORMTEXT ".length() ) )
                        {
                            int k = text.indexOf(PLACEHOLDER, c);
                            if (k >= 0 )
                            {
                                text = text.substring(0, k )
                                        + values[usedVal]
                                        + text.substring(k
                                                + PLACEHOLDER.length());
                                usedVal++;
                                isFound = true;
                            }
                        }
                    }
                    if (isFound )
                    {
                        run.replaceText(oldText, text, 0);
                    }

                }
            }
        }

        String docText = r.text();

        assertTrue(docText.contains("1-1"));
        assertTrue(docText.contains("1-12"));

        assertFalse(docText.contains("1-13"));
        assertFalse(docText.contains("1-15"));
    }

    /**
     * [RESOLVED FIXED] Bug 47731 - Word Extractor considers text copied from
     * some website as an embedded object
     */
    public void test47731() throws Exception
    {
        String foundText = getText("Bug47731.doc");

        assertTrue(foundText
                .contains("Soak the rice in water for three to four hours"));
    }

    /**
     * Bug 4774 - text extracted by WordExtractor is broken
     */
    public void test47742() throws Exception
    {
        // (1) extract text from MS Word document via POI
        String foundText = getText("Bug47742.doc");

        // (2) read text from text document (retrieved by saving the word
        // document as text file using encoding UTF-8)
        InputStream is = POIDataSamples.getDocumentInstance()
                .openResourceAsStream("Bug47742-text.txt");
        try {
            byte[] expectedBytes = IOUtils.toByteArray(is);
            String expectedText = new String(expectedBytes, "utf-8" )
                    .substring(1); // strip-off the unicode marker
    
            assertEqualsIgnoreNewline(expectedText, foundText);
        } finally {
            is.close();
        }
    }

    /**
     * Bug 47958 - Exception during Escher walk of pictures
     */
    public void test47958()
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug47958.doc");
        doc.getPicturesTable().getAllPictures();
    }

    /**
     * [RESOLVED FIXED] Bug 48065 - Problems with save output of HWPF (losing
     * formatting)
     */
    public void test48065()
    {
        HWPFDocument doc1 = HWPFTestDataSamples.openSampleFile("Bug48065.doc");
        HWPFDocument doc2 = HWPFTestDataSamples.writeOutAndReadBack(doc1);

        Range expected = doc1.getRange();
        Range actual = doc2.getRange();

        assertEqualsIgnoreNewline(
                expected.text().replace("\r", "\n").replaceAll("\n\n", "\n" ),
                actual.text().replace("\r", "\n").replaceAll("\n\n", "\n"));

        assertTableStructures(expected, actual);
    }

    public void test49933() throws IOException
    {
        String text = getTextOldFile("Bug49933.doc");

        assertTrue( text.contains( "best.wine.jump.ru" ) );
    }

    /**
     * Bug 50936 - Exception parsing MS Word 8.0 file
     */
    public void test50936_1()
    {
        HWPFDocument hwpfDocument = HWPFTestDataSamples
                .openSampleFile("Bug50936_1.doc");
        hwpfDocument.getPicturesTable().getAllPictures();
    }

    /**
     * Bug 50936 - Exception parsing MS Word 8.0 file
     */
    public void test50936_2()
    {
        HWPFDocument hwpfDocument = HWPFTestDataSamples
                .openSampleFile("Bug50936_2.doc");
        hwpfDocument.getPicturesTable().getAllPictures();
    }

    /**
     * Bug 50936 - Exception parsing MS Word 8.0 file
     */
    public void test50936_3()
    {
        HWPFDocument hwpfDocument = HWPFTestDataSamples
                .openSampleFile("Bug50936_3.doc");
        hwpfDocument.getPicturesTable().getAllPictures();
    }

    /**
     * [FAILING] Bug 50955 - error while retrieving the text file
     */
    public void test50955() throws IOException
    {
        try {
            getTextOldFile("Bug50955.doc");

            fixed("50955");
        } catch (IllegalStateException e) {
            // expected here
        }
    }

    /**
     * [RESOLVED FIXED] Bug 51604 - replace text fails for doc (poi 3.8 beta
     * release from download site )
     */
    public void test51604()
    {
        HWPFDocument document = HWPFTestDataSamples
                .openSampleFile("Bug51604.doc");

        Range range = document.getRange();
        int numParagraph = range.numParagraphs();
        int counter = 0;
        for (int i = 0; i < numParagraph; i++ )
        {
            Paragraph paragraph = range.getParagraph(i);
            int numCharRuns = paragraph.numCharacterRuns();
            for (int j = 0; j < numCharRuns; j++ )
            {
                CharacterRun charRun = paragraph.getCharacterRun(j);
                String text = charRun.text();
                charRun.replaceText(text, "+" + (++counter));
            }
        }

        document = HWPFTestDataSamples.writeOutAndReadBack(document);
        String text = document.getDocumentText();
        assertEqualsIgnoreNewline("+1+2+3+4+5+6+7+8+9+10+11+12", text);
    }

    /**
     * [RESOLVED FIXED] Bug 51604 - replace text fails for doc (poi 3.8 beta
     * release from download site )
     */
    public void test51604p2() throws Exception
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug51604.doc");

        Range range = doc.getRange();
        int numParagraph = range.numParagraphs();
        replaceText(range, numParagraph);

        doc = HWPFTestDataSamples.writeOutAndReadBack(doc);
        final FileInformationBlock fileInformationBlock = doc
                .getFileInformationBlock();

        int totalLength = 0;
        for (SubdocumentType type : SubdocumentType.values() )
        {
            final int partLength = fileInformationBlock
                    .getSubdocumentTextStreamLength(type);
            assert (partLength >= 0);

            totalLength += partLength;
        }
        assertEquals(doc.getText().length(), totalLength);
    }

    private void replaceText(Range range, int numParagraph) {
        for (int i = 0; i < numParagraph; i++ )
        {
            Paragraph paragraph = range.getParagraph(i);
            int numCharRuns = paragraph.numCharacterRuns();
            for (int j = 0; j < numCharRuns; j++ )
            {
                CharacterRun charRun = paragraph.getCharacterRun(j);
                String text = charRun.text();
                if (text.contains("Header" ) )
                    charRun.replaceText(text, "added");
            }
        }
    }

    /**
     * [RESOLVED FIXED] Bug 51604 - replace text fails for doc (poi 3.8 beta
     * release from download site )
     */
    public void test51604p3() throws Exception
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug51604.doc");

        byte[] originalData = new byte[doc.getFileInformationBlock()
                .getLcbDop()];
        System.arraycopy(doc.getTableStream(), doc.getFileInformationBlock()
                .getFcDop(), originalData, 0, originalData.length);

        HWPFOutputStream outputStream = new HWPFOutputStream();
        doc.getDocProperties().writeTo(outputStream);
        final byte[] oldData = outputStream.toByteArray();

        assertEqualsIgnoreNewline(Arrays.toString(originalData ),
                Arrays.toString(oldData));

        Range range = doc.getRange();
        int numParagraph = range.numParagraphs();
        replaceText(range, numParagraph);

        doc = HWPFTestDataSamples.writeOutAndReadBack(doc);

        outputStream = new HWPFOutputStream();
        doc.getDocProperties().writeTo(outputStream);
        final byte[] newData = outputStream.toByteArray();

        assertEqualsIgnoreNewline(Arrays.toString(oldData ), Arrays.toString(newData));
    }

    /**
     * [RESOLVED FIXED] Bug 51671 - HWPFDocument.write based on NPOIFSFileSystem
     * throws a NullPointerException
     */
    public void test51671() throws Exception
    {
        InputStream is = POIDataSamples.getDocumentInstance()
                .openResourceAsStream("empty.doc");
        NPOIFSFileSystem npoifsFileSystem = new NPOIFSFileSystem(is);
        try {
            HWPFDocument hwpfDocument = new HWPFDocument(
                    npoifsFileSystem.getRoot());
            hwpfDocument.write(new ByteArrayOutputStream());
        } finally {
            npoifsFileSystem.close();
        }
    }

    /**
     * Bug 51678 - Extracting text from Bug51524.zip is slow Bug 51524 -
     * PapBinTable constructor is slow
     */
    public void test51678And51524() throws IOException
    {
        // YK: the test will run only if the poi.test.remote system property is
        // set.
        // TODO: refactor into something nicer!
        if (System.getProperty("poi.test.remote" ) != null )
        {
            String href = "http://domex.nps.edu/corp/files/govdocs1/007/007488.doc";
            HWPFDocument hwpfDocument = HWPFTestDataSamples
                    .openRemoteFile(href);

            WordExtractor wordExtractor = new WordExtractor(hwpfDocument);
            try {
                wordExtractor.getText();
            } finally {
                wordExtractor.close();
            }
        }
    }

    /**
     * [FIXED] Bug 51902 - Picture.fillRawImageContent -
     * ArrayIndexOutOfBoundsException
     */
    public void testBug51890()
    {
        HWPFDocument doc = HWPFTestDataSamples.openSampleFile("Bug51890.doc");
        for (Picture picture : doc.getPicturesTable().getAllPictures() )
        {
            PictureType pictureType = picture.suggestPictureType();
            logger.log(POILogger.DEBUG,
                    "Picture at offset " + picture.getStartOffset()
                            + " has type " + pictureType);
        }
    }

    /**
     * [RESOLVED FIXED] Bug 51834 - Opening and Writing .doc file results in
     * corrupt document
     */
    public void testBug51834() throws Exception
    {
        /*
         * we don't have Java test for this file - it should be checked using
         * Microsoft BFF Validator. But check read-write-read anyway. -- sergey
         */
        HWPFTestDataSamples.openSampleFile("Bug51834.doc");
        HWPFTestDataSamples.writeOutAndReadBack(HWPFTestDataSamples
                .openSampleFile("Bug51834.doc"));
    }

    /**
     * Bug 51944 - PAPFormattedDiskPage.getPAPX - IndexOutOfBounds
     */
    public void testBug51944() throws Exception
    {
        HWPFOldDocument doc = HWPFTestDataSamples.openOldSampleFile("Bug51944.doc");
        assertNotNull(WordToTextConverter.getText(doc));
    }

    /**
     * Bug 52032 - [BUG] & [partial-PATCH] HWPF - ArrayIndexOutofBoundsException
     * with no stack trace (broken after revision 1178063)
     */
    public void testBug52032_1() throws Exception
    {
        assertNotNull(getText("Bug52032_1.doc"));
    }

    /**
     * Bug 52032 - [BUG] & [partial-PATCH] HWPF - ArrayIndexOutofBoundsException
     * with no stack trace (broken after revision 1178063)
     */
    public void testBug52032_2() throws Exception
    {
        assertNotNull(getText("Bug52032_2.doc"));
    }

    /**
     * Bug 52032 - [BUG] & [partial-PATCH] HWPF - ArrayIndexOutofBoundsException
     * with no stack trace (broken after revision 1178063)
     */
    public void testBug52032_3() throws Exception
    {
        assertNotNull(getText("Bug52032_3.doc"));
    }

    /**
     * Bug 53380 - ArrayIndexOutOfBounds Exception parsing word 97 document
     */
    public void testBug53380_1() throws Exception
    {
        assertNotNull(getText("Bug53380_1.doc"));
    }

    /**
     * Bug 53380 - ArrayIndexOutOfBounds Exception parsing word 97 document
     */
    public void testBug53380_2() throws Exception
    {
        assertNotNull(getText("Bug53380_2.doc"));
    }

    /**
     * Bug 53380 - ArrayIndexOutOfBounds Exception parsing word 97 document
     */
    public void testBug53380_3() throws Exception
    {
        assertNotNull(getText("Bug53380_3.doc"));
    }

    /**
     * Bug 53380 - ArrayIndexOutOfBounds Exception parsing word 97 document
     */
    public void testBug53380_4() throws Exception
    {
        assertNotNull(getText("Bug53380_4.doc"));
    }
    
    /**
     * java.lang.UnsupportedOperationException: Non-extended character 
     *  Pascal strings are not supported right now
     * 
     * Disabled pending a fix for the bug
     */
    public void test56880() throws Exception {
        HWPFDocument doc =
                HWPFTestDataSamples.openSampleFile("56880.doc");
        assertEqualsIgnoreNewline("Check Request", doc.getRange().text());
    }

    
    // These are the values the are expected to be read when the file
    // is checked.
    private final int section1LeftMargin = 1440;
    private final int section1RightMargin = 1440;
    private final int section1TopMargin = 1440;
    private final int section1BottomMargin = 1440;
    private final int section1NumColumns = 1;
    private int section2LeftMargin = 1440;
    private int section2RightMargin = 1440;
    private int section2TopMargin = 1440;
    private int section2BottomMargin = 1440;
    private final int section2NumColumns = 3;
    
    @SuppressWarnings("SuspiciousNameCombination")
    public void testHWPFSections() {
        HWPFDocument document = HWPFTestDataSamples.openSampleFile("Bug53453Section.doc");
        Range overallRange = document.getOverallRange();
        int numParas = overallRange.numParagraphs();
        for(int i = 0; i < numParas; i++) {
            Paragraph para = overallRange.getParagraph(i);
            int numSections = para.numSections();
            for(int j = 0; j < numSections; j++) {
                Section section = para.getSection(j);
                if(para.text().trim().equals("Section1")) {
                    assertSection1Margin(section);
                }
                else if(para.text().trim().equals("Section2")) {
                    assertSection2Margin(section);
                    
                    // Change the margin widths
                    this.section2BottomMargin = (int)(1.5 * AbstractWordUtils.TWIPS_PER_INCH);
                    this.section2TopMargin = (int)(1.75 * AbstractWordUtils.TWIPS_PER_INCH);
                    this.section2LeftMargin = (int)(0.5 * AbstractWordUtils.TWIPS_PER_INCH);
                    this.section2RightMargin = (int)(0.75 * AbstractWordUtils.TWIPS_PER_INCH);
                    section.setMarginBottom(this.section2BottomMargin);
                    section.setMarginLeft(this.section2LeftMargin);
                    section.setMarginRight(this.section2RightMargin);
                    section.setMarginTop(this.section2TopMargin);
                }
            }
        }
        
        // Save away and re-read the document to prove the chages are permanent
        document = HWPFTestDataSamples.writeOutAndReadBack(document);
        overallRange = document.getOverallRange();
        numParas = overallRange.numParagraphs();
        for(int i = 0; i < numParas; i++) {
            Paragraph para = overallRange.getParagraph(i);
            int numSections = para.numSections();
            for(int j = 0; j < numSections; j++) {
                Section section = para.getSection(j);
                if(para.text().trim().equals("Section1")) {
                    // No changes to the margins in Section1
                    assertSection1Margin(section);
                }
                else if(para.text().trim().equals("Section2")) {
                    // The margins in Section2 have kept the new settings.
                    assertSection2Margin(section);
                }
            }
        }
    }

    @SuppressWarnings("Duplicates")
    private void assertSection1Margin(Section section) {
        assertEquals(section1BottomMargin, section.getMarginBottom());
        assertEquals(section1LeftMargin, section.getMarginLeft());
        assertEquals(section1RightMargin, section.getMarginRight());
        assertEquals(section1TopMargin, section.getMarginTop());
        assertEquals(section1NumColumns, section.getNumColumns());
    }

    @SuppressWarnings("Duplicates")
    private void assertSection2Margin(Section section) {
        assertEquals(section2BottomMargin, section.getMarginBottom());
        assertEquals(section2LeftMargin, section.getMarginLeft());
        assertEquals(section2RightMargin, section.getMarginRight());
        assertEquals(section2TopMargin, section.getMarginTop());
        assertEquals(section2NumColumns, section.getNumColumns());
    }

    public void testRegressionIn315beta2() {
        HWPFDocument hwpfDocument = HWPFTestDataSamples.openSampleFile("cap.stanford.edu_profiles_viewbiosketch_facultyid=4009&name=m_maciver.doc");
        assertNotNull(hwpfDocument);
    }

    public void test57603SevenRowTable() throws Exception {
        try {
            HWPFDocument hwpfDocument = HWPFTestDataSamples.openSampleFile("57603-seven_columns.doc");
            HWPFDocument hwpfDocument2 = HWPFTestDataSamples.writeOutAndReadBack(hwpfDocument);
            assertNotNull(hwpfDocument2);
            hwpfDocument2.close();
            hwpfDocument.close();
            fixed("57603");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected until this bug is fixed
        }
    }
    
    public void test57843() throws IOException {
        File f = POIDataSamples.getDocumentInstance().getFile("57843.doc");
        POIFSFileSystem fs = new POIFSFileSystem(f, true);
        try {
            HWPFOldDocument doc = new HWPFOldDocument(fs);
            assertNotNull(doc);
            doc.close();
            fixed("57843");
        } catch (ArrayIndexOutOfBoundsException e) {
            // expected until this bug is fixed
        } finally {
            fs.close();
        }
    }
}
