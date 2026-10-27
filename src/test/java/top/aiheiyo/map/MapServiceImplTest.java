package top.aiheiyo.map;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ReactiveSettingFetcher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import top.aiheiyo.map.service.helper.MarkerMapHelper;
import top.aiheiyo.map.service.impl.MapServiceImpl;
import static org.junit.jupiter.api.Assertions.*;

class MapServiceImplTest {
    private MapGroup place(String name) {
        MapGroup group = new MapGroup();
        group.setMetadata(new Metadata());
        group.getMetadata().setName(name);
        group.getMetadata().setCreationTimestamp(Instant.parse("2024-01-01T00:00:00Z"));
        group.setSpec(new MapGroup.MapGroupSpec());
        group.getSpec().setDisplayName(name);
        group.getSpec().setCoordinates("104.06,30.67");
        return group;
    }

    @SuppressWarnings("unchecked")
    private MapServiceImpl service(AtomicReference<JsonNode> setting) {
        MapGroup linked = place("linked");
        MapGroup empty = place("empty");
        // The global setting must also control old places carrying the legacy show flag.
        empty.getMetadata().setAnnotations(java.util.Map.of("show", "show"));
        Map marker = new Map();
        marker.setSpec(new Map.MapSpec());
        marker.getSpec().setGroupName("linked");
        marker.getSpec().setPost("post");
        Post post = new Post();
        post.setMetadata(new Metadata());
        post.getMetadata().setName("post");
        post.setSpec(new Post.PostSpec());
        post.getSpec().setTitle("Travel article");
        post.setStatus(new Post.PostStatus());
        post.getStatus().setPermalink("/article");
        ReactiveExtensionClient client = (ReactiveExtensionClient) Proxy.newProxyInstance(
                ReactiveExtensionClient.class.getClassLoader(), new Class[]{ReactiveExtensionClient.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("list")) throw new AssertionError(method);
                    if (args[0] == Map.class) return Flux.just(marker);
                    if (args[0] == MapGroup.class) return Flux.just(linked, empty);
                    if (args[0] == Post.class) return Flux.just(post).filter((Predicate<Post>) args[1]);
                    throw new AssertionError(args[0]);
                });
        ReactiveSettingFetcher fetcher = (ReactiveSettingFetcher) Proxy.newProxyInstance(
                ReactiveSettingFetcher.class.getClassLoader(), new Class[]{ReactiveSettingFetcher.class},
                (proxy, method, args) -> {
                    assertEquals("getSettingValue", method.getName());
                    assertEquals("display", args[0]);
                    return Mono.defer(() -> Mono.justOrEmpty(setting.get()));
                });
        return new MapServiceImpl(client, new MarkerMapHelper(client), fetcher);
    }

    @Test
    void showsEmptyPlacesByDefaultWithoutSavedConfig() {
        var features = service(new AtomicReference<>()).mapFeature().collectList().block();
        assertEquals(2, features.size());
        assertEquals(1L, features.stream().filter(f -> f.getProperties().getNopost()).count());
    }

    @Test
    void savedSettingControlsEmptyPlacesAndAppliesWithoutRestart() {
        var mapper = JsonMapper.builder().build();
        var setting = new AtomicReference<>(mapper.readTree("{\"showPlacesWithoutPosts\":false}"));
        var service = service(setting);
        var hidden = service.mapFeature().collectList().block();
        assertEquals(List.of("linked"), hidden.stream().map(f -> f.getProperties().getTitle()).toList());
        setting.set(mapper.readTree("{\"showPlacesWithoutPosts\":true}"));
        assertEquals(2, service.mapFeature().collectList().block().size());
        setting.set(mapper.readTree("{}"));
        assertEquals(2, service.mapFeature().collectList().block().size());
    }
}
