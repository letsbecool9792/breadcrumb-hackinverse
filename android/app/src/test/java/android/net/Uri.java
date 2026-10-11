package android.net;

public abstract class Uri {
    public static Uri parse(String uriString) {
        return new StringUri(uriString);
    }

    public abstract String toString();

    public String getScheme() {
        return null;
    }

    private static class StringUri extends Uri {
        private final String uriString;

        StringUri(String uriString) {
            this.uriString = uriString;
        }

        @Override
        public String toString() {
            return uriString;
        }

        @Override
        public String getScheme() {
            if (uriString == null) return null;
            int idx = uriString.indexOf(':');
            return idx != -1 ? uriString.substring(0, idx) : null;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            StringUri stringUri = (StringUri) o;
            return uriString != null ? uriString.equals(stringUri.uriString) : stringUri.uriString == null;
        }

        @Override
        public int hashCode() {
            return uriString != null ? uriString.hashCode() : 0;
        }
    }
}
