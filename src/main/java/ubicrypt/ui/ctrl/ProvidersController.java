package ubicrypt.ui.ctrl;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import ubicrypt.ui.Anchor;

public class ProvidersController implements Initializable {
    @FXML
    VBox root;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {


    }

    public void addFile(MouseEvent mouseEvent) throws IOException {
        System.out.println(root.getUserData());
        Parent file = FXMLLoader.load(ProvidersController.class.getResource("/fxml/provider/file.fxml"), new Anchor.ResourceBundleWrapper(ResourceBundle.getBundle("fx")));
        ((Pane) root.getParent()).getChildren().setAll(file);
    }
}
