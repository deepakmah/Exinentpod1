package nutridyn;

/**
 * Central URLs, output paths, and test credentials. Move secrets to env vars for production use.
 */
public final class NutridynConfig {

    private NutridynConfig() {
    }

    public static final String OUTPUT_ROOT = "C:\\Users\\deepa\\Documents\\Automation\\Nutridyn";
    public static final String BASE_URL = "https://nutridyn.com/";
    public static final String SITEMAP_URL = "https://nutridyn.com/pub/media/sitemap.xml";

    public static final String PATIENT_EMAIL = "gopal.exinentpatient@gmail.com";
    public static final String PATIENT_PASSWORD = "test@1234";

    public static final String PRACTITIONER_EMAIL = "foo.bar@getastra.live";
    public static final String PRACTITIONER_PASSWORD = "123456";
}
