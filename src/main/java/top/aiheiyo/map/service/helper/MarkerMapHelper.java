package top.aiheiyo.map.service.helper;

import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.comparator.Comparators;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Post;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import top.aiheiyo.map.Map;
import top.aiheiyo.map.MapGroup;
import top.aiheiyo.map.vo.MapGroupVo;
import top.aiheiyo.map.vo.MapQuery;
import top.aiheiyo.map.vo.MapVo;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Description:
 *
 * @author : evan  Date: 2024/5/7
 */
@Component
public class MarkerMapHelper {

    private final ReactiveExtensionClient client;

    public MarkerMapHelper(ReactiveExtensionClient client) {
        this.client = client;
    }

    public Flux<MapGroupVo> groupBy() {
        Flux<Map> mapFlux = listAll(null);
        return listAllGroups()
                .concatMap(group -> mapFlux
                        .filter(map -> StringUtils.equals(map.getSpec().getGroupName(),
                                group.getMetadata().getName())
                        )
                        .map(MapVo::from)
                        .collectList()
                        .map(group::withLinks)
                        .defaultIfEmpty(group)
                )
                .mergeWith(Mono.defer(() -> ungrouped()
                        .map(MapGroupVo::from)
                        .flatMap(mapGroup -> mapFlux.filter(
                                        map -> StringUtils.isBlank(map.getSpec().getGroupName()))
                                .map(MapVo::from)
                                .collectList()
                                .map(mapGroup::withLinks)
                                .defaultIfEmpty(mapGroup)
                        ))
                );
    }

    public Mono<MapGroup> ungrouped() {
        MapGroup mapGroup = new MapGroup();
        mapGroup.setMetadata(new Metadata());
        mapGroup.getMetadata().setName("ungrouped");
        mapGroup.setSpec(new MapGroup.MapGroupSpec());
        mapGroup.getSpec().setDisplayName("");
        mapGroup.getSpec().setCoordinates("");
        mapGroup.getSpec().setPriority(0);
        return Mono.just(mapGroup);
    }

    public Flux<Map> listAll(@Nullable Predicate<Map> predicate) {
        return client.list(Map.class, predicate, defaultMapComparator());
    }

    public Flux<MapGroupVo> listAllGroups() {
        return client.list(MapGroup.class, null, defaultGroupComparator())
                .map(MapGroupVo::from);
    }

    public static Comparator<MapGroup> defaultGroupComparator() {
        Function<MapGroup, Integer> priority = group -> group.getSpec().getPriority();
        Function<MapGroup, Instant> createTime =
                group -> group.getMetadata().getCreationTimestamp();
        Function<MapGroup, String> name = group -> group.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
                .thenComparing(createTime)
                .thenComparing(name);
    }

    public static Comparator<Map> defaultMapComparator() {
        Function<Map, Integer> priority = map -> map.getSpec().getPriority();
        Function<Map, Instant> createTime = map -> map.getMetadata().getCreationTimestamp();
        Function<Map, String> name = map -> map.getMetadata().getName();
        return Comparator.comparing(priority, Comparators.nullsLow())
                .thenComparing(createTime)
                .thenComparing(name);
    }

    public Mono<ListResult<Map>> listMap(MapQuery query) {
        return client.listAll(Map.class, query.toListOptions(), Sort.unsorted())
                .filter(query.toPredicate())
                .sort(query.toComparator())
                .collectList()
                .map(maps -> new ListResult<>(query.getPage(), query.getSize(), maps.size(),
                        query.getPage() <= 0 || query.getSize() <= 0
                                ? maps : ListResult.subList(maps, query.getPage(), query.getSize())))
                .flatMap(result -> {
                    List<Map> maps = result.getItems();
                    Flux<Post> postFlux = Flux.fromIterable(maps)
                            .filter(map -> StringUtils.isNotBlank(map.getSpec().getPost()))
                            .map(map -> map.getSpec().getPost())
                            .distinct()
                            .flatMap(postName -> client.fetch(Post.class, postName));

                    return postFlux.collectList()
                            .map(posts -> {
                                java.util.Map<String, Post> postMap = posts.stream().collect(Collectors.toMap(post -> post.getMetadata().getName(), post -> post));
                                for (Map map : maps) {
                                    Post post = postMap.get(map.getSpec().getPost());
                                    if (Objects.nonNull(post)) {
                                        map.getSpec().setDisplayName(post.getSpec().getTitle());
                                        map.getSpec().setUrl(post.getStatus().getPermalink());
                                    }
                                }
                                return result;
                            });
                });
    }
}

