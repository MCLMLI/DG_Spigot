package dg.enums;

public enum DGChannel {
    A,
    B,
    BOTH;

    public boolean contains(DGChannel channel) {
        if (!(channel == A || channel == B)) {
            throw new IllegalArgumentException("Invalid channel for test: " + channel);
        }
        return this == channel || this == BOTH;
    }
}
