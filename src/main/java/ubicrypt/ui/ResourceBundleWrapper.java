package ubicrypt.ui;

import java.util.Enumeration;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

public class ResourceBundleWrapper extends ResourceBundle {

    private final ResourceBundle bundle;

    public ResourceBundleWrapper(final ResourceBundle bundle) {
        this.bundle = bundle;
    }

    @Override
    protected Object handleGetObject(final String key) {
        return bundle.getObject(key);
    }

    @Override
    public Enumeration<String> getKeys() {
        return bundle.getKeys();
    }

    @Override
    public boolean containsKey(final String key) {
        return bundle.containsKey(key);
    }

    @Override
    public Locale getLocale() {
        return bundle.getLocale();
    }

    @Override
    public Set<String> keySet() {
        return bundle.keySet();
    }

}
