package io.github.flowerjvm.flower.studio.view;

/** Safe local artifact reference discovered in an event attribute. */
public final class ArtifactLinkView {

    private final String attributePath;
    private final String artifactId;
    private final String location;
    private final String mediaType;
    private final long sizeBytes;
    private final String sha256;
    private final String downloadUrl;

    public ArtifactLinkView(
            String attributePath,
            String artifactId,
            String location,
            String mediaType,
            long sizeBytes,
            String sha256,
            String downloadUrl) {
        this.attributePath = attributePath;
        this.artifactId = artifactId;
        this.location = location;
        this.mediaType = mediaType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.downloadUrl = downloadUrl;
    }

    public String getAttributePath() {
        return attributePath;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getLocation() {
        return location;
    }

    public String getMediaType() {
        return mediaType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
