package be.nabu.eai.module.glue.pdf.viewer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import be.nabu.eai.developer.api.ResourceManager;
import be.nabu.eai.developer.api.ResourceManagerInstance;
import be.nabu.libs.resources.ResourceReadableContainer;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;

public class PDFResourceManager implements ResourceManager {

	private static final double ZOOM_INCREMENT = 0.05;
	
	public static final int DPI = Integer.parseInt(System.getProperty("pdf.dpi", "200"));
	
	@Override
	public ResourceManagerInstance manage(Resource resource) {
		if ("application/pdf".equals(resource.getContentType())) {
			return new PDFResourceManagerInstance(resource);
		}
		return null;
	}
	
	public static class PDFResourceManagerInstance implements ResourceManagerInstance {

		private Resource resource;
		private DoubleProperty zoom = new SimpleDoubleProperty(1);

		public PDFResourceManagerInstance(Resource resource) {
			this.resource = resource;
		}

		@Override
		public void save() {
			// do nothing
		}

		@SuppressWarnings("unchecked")
		@Override
		public Node getView() {
			try {
				PDDocument document = load();
				VBox vbox = new VBox();
				TabPane tabs = new TabPane();
				tabs.setSide(Side.LEFT);
				List<PDPage> pages = document.getDocumentCatalog().getAllPages();
				int i = 1;
				HBox meta = new HBox();
				VBox.setVgrow(meta, Priority.NEVER);
				Label scale = new Label("Scale: 100%");
				Label position = new Label();
				meta.getChildren().addAll(scale, position);
				for (PDPage page : pages) {
					Tab tab = new Tab("Page " + i++);
					BufferedImage expectedImage = page.convertToImage(BufferedImage.TYPE_INT_RGB, DPI);
					ByteArrayOutputStream output = new ByteArrayOutputStream();
					ImageIO.write(expectedImage, "png", output);
					final Image image = new Image(new ByteArrayInputStream(output.toByteArray()));
					final ImageView imageView = new ImageView(image);
					ScrollPane anchor = new ScrollPane();
					// substract some pixels for the positioning (position.heightProperty() is not working?)
					anchor.prefHeightProperty().bind(vbox.heightProperty());
					tab.setContent(anchor);
					anchor.setContent(imageView);
					tabs.getTabs().add(tab);
					imageView.setPreserveRatio(true);
					// substract some pixels for the left handed tabs
					imageView.fitWidthProperty().bind(zoom.multiply(vbox.widthProperty().subtract(35)));
					imageView.addEventHandler(ScrollEvent.SCROLL, new EventHandler<ScrollEvent>() {
						@Override
						public void handle(ScrollEvent event) {
							if (event.isControlDown()) {
								double zoomIncrement = ZOOM_INCREMENT * (event.getDeltaY() / event.getMultiplierY());
								double newValue = zoom.get() + zoomIncrement;
								if (newValue > 0) {
									zoom.set(newValue);
									scale.setText("Scale: " + (int) (100 * newValue) + "%");
								}
								event.consume();
							}
						}
					});
					imageView.addEventHandler(MouseEvent.MOUSE_MOVED, new EventHandler<MouseEvent>() {
						@Override
						public void handle(MouseEvent event) {
							double fitFactor = imageView.getFitWidth() / image.getWidth();
							// the fit factor already incorporates the zoom so no need to take that into account (because the zoom basically resizes)
							double x = event.getX() / fitFactor;
							double y = event.getY() / fitFactor;
							position.setText("Position: " + (int) x + ", " + (int) y);
						}
					});
				}
				vbox.getChildren().addAll(tabs, meta);
//				vbox.prefHeightProperty().bind(pane.heightProperty());
				return vbox;
			}
			catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		private PDDocument load() throws IOException {
			ReadableContainer<ByteBuffer> readable = new ResourceReadableContainer((ReadableResource) resource);
			try {
				return PDDocument.load(IOUtils.toInputStream(readable));
			}
			finally {
				readable.close();
			}
		}
		
	}

}
