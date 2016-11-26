package ubicrypt.ui;

import com.google.common.base.Throwables;

import org.springframework.context.ConfigurableApplicationContext;

import javafx.util.Callback;

import static ubicrypt.core.Utils.springIt;

public class ControllerFactory implements Callback<Class<?>, Object> {
    private final ConfigurableApplicationContext ctx;

    public ControllerFactory(ConfigurableApplicationContext ctx) {
        this.ctx = ctx;
        ctx.getBeanFactory().registerSingleton("controllerFactory", this);
    }

    @Override
    public Object call(Class<?> aClass) {
        try {
            return springIt(ctx, aClass.newInstance());
        } catch (Exception e) {
            Throwables.propagate(e);
            return null;
        }
    }
}
