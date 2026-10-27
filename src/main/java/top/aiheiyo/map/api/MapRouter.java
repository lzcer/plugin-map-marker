package top.aiheiyo.map.api;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RouterFunctions.nest;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springdoc.core.fn.builders.arrayschema.Builder.arraySchemaBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import org.springdoc.core.fn.builders.parameter.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import top.aiheiyo.map.service.helper.MarkerMapHelper;
import top.aiheiyo.map.vo.MapQuery;

/**
 * Description: Router
 *
 * @author : evan  Date: 2024/4/25
 */
@Configuration
public class MapRouter {

    private final MarkerMapHelper markerMapHelper;

    private final String tag = "api.plugin.aiheiyo.top/v1alpha1/Link";

    public MapRouter(MarkerMapHelper markerMapHelper) {
        this.markerMapHelper = markerMapHelper;
    }

    @Bean
    RouterFunction<ServerResponse> mapTemplateRoute() {
        return route(GET("/maps").or(GET("/footprints")),
                request -> ServerResponse.ok()
                        .render("maps"));
    }

    @Bean
    RouterFunction<ServerResponse> mapRoute() {
        // Use WebFlux nesting to avoid the Springdoc overload removed in Halo 2.27.
        return nest(RequestPredicates.path("/apis/api.plugin.aiheiyo.top/v1alpha1/plugins/PluginMaps"),
                nested());
    }

    RouterFunction<ServerResponse> nested() {
        return SpringdocRouteBuilder.route()
                .GET("/maps", this::listMapByGroup,
                        builder -> {
                            builder.operationId("listMaps")
                                    .description("Lists map by query parameters")
                                    .tag(tag)
                                    .parameter(queryParameter("page", "integer", "Page number"))
                                    .parameter(queryParameter("size", "integer", "Page size"))
                                    .parameter(queryParameter("keyword", "string", "Keyword to search maps"))
                                    .parameter(queryParameter("groupName", "string", "Map group name"))
                                    .parameter(queryParameter("notContainPost", "string", "Post to exclude"))
                                    .parameter(queryParameter("labelSelector", "string", "Label selector"))
                                    .parameter(queryParameter("fieldSelector", "string", "Field selector"))
                                    .parameter(parameterBuilder().in(ParameterIn.QUERY).name("sort")
                                            .description("Sort field and direction, for example priority,asc")
                                            .array(arraySchemaBuilder().schema(schemaBuilder().type("string"))));
                        }
                ).build();
    }

    private static Builder queryParameter(String name, String type, String description) {
        return parameterBuilder().in(ParameterIn.QUERY).name(name).description(description)
                .schema(schemaBuilder().type(type));
    }

    Mono<ServerResponse> listMapByGroup(ServerRequest request) {
        MapQuery query = new MapQuery(request.exchange());
        return markerMapHelper.listMap(query)
                .flatMap(maps -> ServerResponse.ok().bodyValue(maps));
    }
}
