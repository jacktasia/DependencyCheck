/*
 * This file is part of dependency-check-core.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2014 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.data.nexus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

/**
 * Class of methods to search Nexus repositories.
 *
 * @author colezlaw
 */
public class NexusSearch {

    /**
     * The root URL for the Nexus repository service
     */
    private final URL rootURL;

    /**
     * Used for logging.
     */
    private static final Logger LOGGER = Logger.getLogger(NexusSearch.class.getName());

    /**
     * Creates a NexusSearch for the given repository URL.
     *
     * @param rootURL the root URL of the repository on which searches should execute. full URL's are calculated
     * relative to this URL, so it should end with a /
     */
    public NexusSearch(URL rootURL) {
        this.rootURL = rootURL;
    }

    /**
     * Searches the configured Nexus repository for the given sha1 hash. If the artifact is found, a
     * <code>MavenArtifact</code> is populated with the coordinate information.
     *
     * @param sha1 The SHA-1 hash string for which to search
     * @return the populated Maven coordinates
     * @throws IOException if it's unable to connect to the specified repositor or if the specified artifact is not
     * found.
     */
    public MavenArtifact searchSha1(String sha1) throws IOException {
        if (null == sha1 || !sha1.matches("^[0-9A-Fa-f]{40}$")) {
            throw new IllegalArgumentException("Invalid SHA1 format");
        }

        final URL url = new URL(rootURL, String.format("identify/sha1/%s", sha1.toLowerCase()));

        LOGGER.fine(String.format("Searching Nexus url %s", url.toString()));

        final URLConnection conn = url.openConnection();
        conn.setDoOutput(true);

        // JSON would be more elegant, but there's not currently a dependency
        // on JSON, so don't want to add one just for this
        conn.addRequestProperty("Accept", "application/xml");
        conn.connect();

        try {
            final DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            final Document doc = builder.parse(conn.getInputStream());
            final XPath xpath = XPathFactory.newInstance().newXPath();
            final String groupId = xpath.evaluate("/org.sonatype.nexus.rest.model.NexusArtifact/groupId", doc);
            final String artifactId = xpath.evaluate("/org.sonatype.nexus.rest.model.NexusArtifact/artifactId", doc);
            final String version = xpath.evaluate("/org.sonatype.nexus.rest.model.NexusArtifact/version", doc);
            final String link = xpath.evaluate("/org.sonatype.nexus.rest.model.NexusArtifact/artifactLink", doc);
            return new MavenArtifact(groupId, artifactId, version, link);
        } catch (FileNotFoundException fnfe) {
            // This is what we get when the SHA1 they sent doesn't exist in Nexus. This
            // is useful upstream for recovery, so we just re-throw it
            throw fnfe;
        } catch (Exception e) {
            // Anything else is jacked-up XML stuff that we really can't recover from well
            throw new IOException(e.getMessage(), e);
        }
    }
}

// vim: cc=120:sw=4:ts=4:sts=4
