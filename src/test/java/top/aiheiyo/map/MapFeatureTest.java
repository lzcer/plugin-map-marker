package top.aiheiyo.map;

import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;
import top.aiheiyo.map.vo.MapFeature;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MapFeatureTest {
    private MapGroup place(String coordinates) {
        MapGroup group = new MapGroup();
        group.setMetadata(new Metadata());
        group.getMetadata().setName("place-test");
        group.setSpec(new MapGroup.MapGroupSpec());
        group.getSpec().setDisplayName("成都");
        group.getSpec().setCoordinates(coordinates);
        return group;
    }

    @Test
    void placeWithoutArticlesOrAnnotationsIsVisible() {
        var feature = MapFeature.FeaturesDTO.from(place("104.06,30.67"));
        assertTrue(feature.valid());
        assertTrue(feature.getProperties().getNopost());
        assertEquals(List.of(104.06, 30.67), feature.getGeometry().getCoordinates());
    }

    @Test
    void linkedArticlesKeepTheirLinksAndImage() {
        var feature = MapFeature.FeaturesDTO.from(place("104.06,30.67"));
        feature.getProperties().setPermalink(List.of("/article"));
        feature.getProperties().setDescription(List.of("旅行记录"));
        feature.getProperties().setImage("/cover.jpg");
        assertTrue(feature.valid());
        assertFalse(feature.getProperties().getNopost());
        assertEquals("/cover.jpg", feature.getProperties().getImage());
    }

    @Test
    void legacyShowMetadataCanRemain() {
        MapGroup group = place("104.06,30.67");
        group.getMetadata().setAnnotations(java.util.Map.of("show", "show"));
        assertTrue(MapFeature.FeaturesDTO.from(group).valid());
        assertEquals("show", group.getMetadata().getAnnotations().get("show"));
    }

    @Test
    void invalidCoordinatesDoNotBreakMapData() {
        for (String coordinates : List.of("", "bad,30", "104", "104,", "104,,30", "NaN,30", "181,30", "104,91", "104,30,1")) {
            assertFalse(MapFeature.FeaturesDTO.from(place(coordinates)).valid(), coordinates);
        }
        assertFalse(MapFeature.FeaturesDTO.from(place(null)).valid());
        assertFalse(MapFeature.FeaturesDTO.from(null).valid());
    }
}
