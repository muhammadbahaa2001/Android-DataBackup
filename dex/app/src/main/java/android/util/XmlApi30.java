/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.util;

import android.os.Build;
import android.os.SystemProperties;
import android.system.ErrnoException;
import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.util.BinaryXmlPullParser;
import com.android.internal.util.BinaryXmlSerializer;
import com.android.internal.util.FastXmlSerializer;
import com.android.internal.util.XmlUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import libcore.util.XmlObjectFactory;

/**
 * XML utility methods.
 */
public class XmlApi30 {
    private XmlApi30() {
    }

    /**
     * {@link XmlPullParser} "relaxed" feature name.
     *
     * @see <a href="http://xmlpull.org/v1/doc/features.html#relaxed">
     * specification</a>
     */
    public static String FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed";

    /**
     * Feature flag: when set, {@link #resolveSerializer(OutputStream)} will
     * emit binary XML by default.
     *
     * @hide
     */
    public static final boolean ENABLE_BINARY_DEFAULT = SystemProperties
            .getBoolean("persist.sys.binary_xml", true);

    /**
     * Returns a new pull parser with namespace support.
     */
    public static XmlPullParser newPullParser() {
        try {
            XmlPullParser parser = XmlObjectFactory.newXmlPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, true);
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            return parser;
        } catch (XmlPullParserException e) {
            throw new AssertionError();
        }
    }

    /**
     * Creates a new {@link TypedXmlPullParser} which is optimized for use
     * inside the system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     *
     * @hide
     */
    @SuppressWarnings("AndroidFrameworkEfficientXml")
    public static @NonNull TypedXmlPullParser newFastPullParser() {
        return XmlUtils.makeTyped(newPullParser());
    }

    /**
     * Creates a new {@link XmlPullParser} that reads XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 8.5x
     * faster than {@code Xml.newFastPullParser()} for a typical
     * {@code packages.xml}.
     *
     * @hide
     */
    public static @NonNull TypedXmlPullParser newBinaryPullParser() {
        return new BinaryXmlPullParser();
    }

    /**
     * Creates a new {@link XmlPullParser} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * This returned instance may be configured to read using an efficient
     * binary format instead of a human-readable text format, depending on
     * device feature flags.
     * <p>
     * To ensure that both formats are detected and transparently handled
     * correctly, you must shift to using both {@link #resolveSerializer} and
     * {@link #resolvePullParser}.
     *
     * @hide
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    public static @NonNull TypedXmlPullParser resolvePullParser(@NonNull InputStream in)
            throws IOException {
        final byte[] magic = new byte[4];
        if (in instanceof FileInputStream) {
            try {
                Os.pread(((FileInputStream) in).getFD(), magic, 0, magic.length, 0);
            } catch (ErrnoException e) {
                throw e.rethrowAsIOException();
            }
        } else {
            if (!in.markSupported()) {
                in = new BufferedInputStream(in);
            }
            in.mark(8);
            in.read(magic);
            in.reset();
        }

        final TypedXmlPullParser xml;
        if (Arrays.equals(magic, BinaryXmlSerializer.PROTOCOL_MAGIC_VERSION_0)) {
            xml = newBinaryPullParser();
        } else {
            xml = newFastPullParser();
        }
        try {
            xml.setInput(in, StandardCharsets.UTF_8.name());
        } catch (XmlPullParserException e) {
            throw new IOException(e);
        }
        return xml;
    }

    /**
     * Creates a new xml serializer.
     */
    public static XmlSerializer newSerializer() {
        return XmlObjectFactory.newXmlSerializer();
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * In particular, the returned parser does not support namespaces, prefixes,
     * properties, or options.
     *
     * @hide
     */
    @SuppressWarnings("AndroidFrameworkEfficientXml")
    public static @NonNull TypedXmlSerializer newFastSerializer() {
        return XmlUtils.makeTyped(new FastXmlSerializer());
    }

    /**
     * Creates a new {@link XmlSerializer} that writes XML documents using a
     * custom binary wire protocol which benchmarking has shown to be 4.4x
     * faster and use 2.8x less disk space than {@code Xml.newFastSerializer()}
     * for a typical {@code packages.xml}.
     *
     * @hide
     */
    public static @NonNull TypedXmlSerializer newBinarySerializer() {
        return new BinaryXmlSerializer();
    }

    /**
     * Creates a new {@link XmlSerializer} which is optimized for use inside the
     * system, typically by supporting only a basic set of features.
     * <p>
     * This returned instance may be configured to write using an efficient
     * binary format instead of a human-readable text format, depending on
     * device feature flags.
     * <p>
     * To ensure that both formats are detected and transparently handled
     * correctly, you must shift to using both {@link #resolveSerializer} and
     * {@link #resolvePullParser}.
     *
     * @hide
     */
    public static @NonNull TypedXmlSerializer resolveSerializer(@NonNull OutputStream out)
            throws IOException {
        final TypedXmlSerializer xml;
        if (ENABLE_BINARY_DEFAULT) {
            xml = newBinarySerializer();
        } else {
            xml = newFastSerializer();
        }
        xml.setOutput(out, StandardCharsets.UTF_8.name());
        return xml;
    }

    /**
     * Copy the first XML document into the second document.
     * <p>
     * Implemented by reading all events from the given {@link XmlPullParser}
     * and writing them directly to the given {@link XmlSerializer}. This can be
     * useful for transparently converting between underlying wire protocols.
     *
     * @hide
     */
    public static void copy(@NonNull XmlPullParser in, @NonNull XmlSerializer out)
            throws XmlPullParserException, IOException {
        // Some parsers may have already consumed the event that starts the
        // document, so we manually emit that event here for consistency
        if (in.getEventType() == XmlPullParser.START_DOCUMENT) {
            out.startDocument(in.getInputEncoding(), true);
        }

        while (true) {
            final int token = in.nextToken();
            switch (token) {
                case XmlPullParser.START_DOCUMENT:
                    out.startDocument(in.getInputEncoding(), true);
                    break;
                case XmlPullParser.END_DOCUMENT:
                    out.endDocument();
                    return;
                case XmlPullParser.START_TAG:
                    out.startTag(normalizeNamespace(in.getNamespace()), in.getName());
                    for (int i = 0; i < in.getAttributeCount(); i++) {
                        out.attribute(normalizeNamespace(in.getAttributeNamespace(i)),
                                in.getAttributeName(i), in.getAttributeValue(i));
                    }
                    break;
                case XmlPullParser.END_TAG:
                    out.endTag(normalizeNamespace(in.getNamespace()), in.getName());
                    break;
                case XmlPullParser.TEXT:
                    out.text(in.getText());
                    break;
                case XmlPullParser.CDSECT:
                    out.cdsect(in.getText());
                    break;
                case XmlPullParser.ENTITY_REF:
                    out.entityRef(in.getName());
                    break;
                case XmlPullParser.IGNORABLE_WHITESPACE:
                    out.ignorableWhitespace(in.getText());
                    break;
                case XmlPullParser.PROCESSING_INSTRUCTION:
                    out.processingInstruction(in.getText());
                    break;
                case XmlPullParser.COMMENT:
                    out.comment(in.getText());
                    break;
                case XmlPullParser.DOCDECL:
                    out.docdecl(in.getText());
                    break;
                default:
                    throw new IllegalStateException("Unknown token " + token);
            }
        }
    }

    /**
     * Some parsers may return an empty string {@code ""} when a namespace in
     * unsupported, which can confuse serializers. This method normalizes empty
     * strings to be {@code null}.
     */
    private static @Nullable String normalizeNamespace(@Nullable String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return null;
        } else {
            return namespace;
        }
    }

    /**
     * Supported character encodings.
     */
    public enum Encoding {

        US_ASCII("US-ASCII"),
        UTF_8("UTF-8"),
        UTF_16("UTF-16"),
        ISO_8859_1("ISO-8859-1");

        final String expatName;

        Encoding(String expatName) {
            this.expatName = expatName;
        }
    }
}