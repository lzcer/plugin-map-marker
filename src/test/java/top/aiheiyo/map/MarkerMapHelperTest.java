package top.aiheiyo.map;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.data.domain.Sort;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.core.extension.content.Post;
import top.aiheiyo.map.vo.MapQuery;
import top.aiheiyo.map.service.helper.MarkerMapHelper;

class MarkerMapHelperTest {
  static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }
  static Map marker(String name, String group, String title, int priority, String post) {
    Map m = new Map();
    m.setMetadata(new Metadata());
    m.getMetadata().setName(name);
    m.getMetadata().setCreationTimestamp(Instant.parse("2024-01-01T00:00:00Z"));
    m.setSpec(new Map.MapSpec());
    m.getSpec().setGroupName(group);
    m.getSpec().setDisplayName(title);
    m.getSpec().setUrl("/"+name);
    m.getSpec().setPriority(priority);
    m.getSpec().setPost(post);
    return m;
  }
  static MapQuery query(String group, String keyword, int page, int size, String exclude) {
    MultiValueMap<String,String> params = new LinkedMultiValueMap<>();
    params.add("groupName",group);params.add("keyword",keyword);
    params.add("page",""+page);params.add("size",""+size);
    if (exclude != null) params.add("notContainPost",exclude);
    return new MapQuery(params) {
      @Override public Sort getSort() { return Sort.by("priority").ascending(); }
    };
  }
  @Test
  void listsMapsOnHalo227() {
    List<Map> maps = List.of(
      marker("a","g1","Chengdu",2,""),
      marker("b","g1","Shanghai",1,"missing"),
      marker("c","g2","Chengdu",0,null),
      marker("d","g1","Chengdu East",3,"existing")
    );
    Post post = new Post();post.setMetadata(new Metadata());post.getMetadata().setName("existing");
    post.setSpec(new Post.PostSpec());post.getSpec().setTitle("Article title");
    post.setStatus(new Post.PostStatus());post.getStatus().setPermalink("/article");
    ReactiveExtensionClient client = (ReactiveExtensionClient)Proxy.newProxyInstance(
      ReactiveExtensionClient.class.getClassLoader(),new Class[]{ReactiveExtensionClient.class},
      (proxy,method,arguments) -> {
        if (method.getName().equals("listAll")) return Flux.fromIterable(maps);
        if (method.getName().equals("fetch")) {
          check(arguments[1] != null && !((String)arguments[1]).isBlank(), "No blank post fetch");
          return arguments[1].equals("existing") ? Mono.just(post) : Mono.empty();
        }
        throw new AssertionError("Unexpected API: "+method);
      });
    MarkerMapHelper helper = new MarkerMapHelper(client);
    var first = helper.listMap(query("g1","",1,1,null)).block();
    check(first.getTotal()==3 && first.getItems().get(0).getMetadata().getName().equals("b"), "Group filter, sorting, page one, missing post");
    var second = helper.listMap(query("g1","",2,1,null)).block();
    check(second.getTotal()==3 && second.getItems().get(0).getMetadata().getName().equals("a"), "Page two, blank post");
    var keywords = helper.listMap(query("g1","CHENGDU",1,20,null)).block();
    check(keywords.getTotal()==2, "Case insensitive keyword and group filter");
    check(keywords.getItems().get(1).getSpec().getDisplayName().equals("Article title"), "Article enrichment");
    check(helper.listMap(query("g1","",9,1,null)).block().getItems().isEmpty(), "Out of range page");
    check(helper.listMap(query("g1","",0,1,null)).block().getItems().size()==3, "Unpaged request");
    check(helper.listMap(query("g1","",1,20,"existing")).block().getTotal()==2, "Exclude post");
    var selector = query("g1","",1,20,null);
    check(selector.toListOptions()!=null,"Halo 2.27 selector API");

  }
}
