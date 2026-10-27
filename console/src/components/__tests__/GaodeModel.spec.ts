import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount } from "@vue/test-utils";
import { defineComponent, nextTick } from "vue";
import GaodeModel from "../GaodeModel.vue";

vi.mock("@halo-dev/api-client", () => ({ axiosInstance: { get: vi.fn() } }));
vi.mock("@amap/amap-jsapi-loader", () => ({ default: { load: vi.fn() } }));
vi.mock("@halo-dev/components", () => ({
  VModal: { template: '<div><slot/><slot name="footer"/></div>' },
  VSpace: { template: "<div><slot/></div>" },
  VButton: {
    props: ["disabled"],
    template: '<button :disabled="disabled"><slot/></button>',
  },
  Toast: { warning: vi.fn(), success: vi.fn() },
}));
import { axiosInstance } from "@halo-dev/api-client";
import AMapLoader from "@amap/amap-jsapi-loader";
import GroupEditingModal from "../GroupEditingModal.vue";

let click: (event: unknown) => void;
let searchCallback: (status: string, result: unknown) => void;
const destroy = vi.fn();
const nodeInput = vi.fn();
const wrappers: ReturnType<typeof mount>[] = [];
const point = (lng = 104.06, lat = 30.67) => ({
  getLng: () => lng,
  getLat: () => lat,
});
function render(props: Record<string, unknown> = {}) {
  const wrapper = mount(GaodeModel, { props: { visible: true, ...props } });
  wrappers.push(wrapper);
  return wrapper;
}
const button = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAll("button").find((b) => b.text() === label)!;
beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(axiosInstance.get).mockResolvedValue({
    data: { gaode: { key: "test", securityJsCode: "test" } },
  });
  vi.mocked(AMapLoader.load).mockResolvedValue({
    Map: class {
      on(_name: string, handler: typeof click) {
        click = handler;
      }
      setCenter() {}
      destroy = destroy;
    },
    Marker: class {
      setPosition() {}
      setMap() {}
    },
    PlaceSearch: class {
      search(_word: string, callback: typeof searchCallback) {
        searchCallback = callback;
      }
    },
  });
});
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()));

describe("地图解析", () => {
  it("uses the Halo 2.27 JSON config API and confirms a selected map position", async () => {
    const wrapper = render();
    await flushPromises();
    expect(axiosInstance.get).toHaveBeenCalledWith(
      "/apis/api.console.halo.run/v1alpha1/plugins/PluginMaps/json-config"
    );
    expect(button(wrapper, "确定").attributes("disabled")).toBeDefined();
    click({ lnglat: point() });
    await nextTick();
    await button(wrapper, "确定").trigger("click");
    expect(wrapper.emitted("confirm")).toEqual([["104.06,30.67"]]);
    expect(wrapper.emitted("update:visible")).toEqual([[false]]);
  });

  it("preserves existing coordinates and cancels without filling the form", async () => {
    const wrapper = render({ initialCoordinates: "120.1,30.2" });
    await flushPromises();
    expect(wrapper.find("input[readonly]").element.value).toBe("120.1,30.2");
    await button(wrapper, "取消").trigger("click");
    expect(wrapper.emitted("confirm")).toBeUndefined();
  });

  it("shows a useful config error and retries", async () => {
    vi.mocked(axiosInstance.get).mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 404 },
    });
    const wrapper = render();
    await flushPromises();
    expect(wrapper.find('[role="alert"]').text()).toContain(
      "插件配置接口不可用"
    );
    expect(AMapLoader.load).not.toHaveBeenCalled();
    await button(wrapper, "重试").trigger("click");
    await flushPromises();
    expect(AMapLoader.load).toHaveBeenCalledTimes(1);
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
  });

  it("disables confirm while searching and fills a search result", async () => {
    const wrapper = render();
    await flushPromises();
    await wrapper.find('input[aria-label="地址"]').setValue("成都");
    await button(wrapper, "搜索").trigger("click");
    expect(button(wrapper, "确定").attributes("disabled")).toBeDefined();
    searchCallback("complete", {
      poiList: { pois: [{ location: point(), name: "成都" }] },
    });
    await nextTick();
    await button(wrapper, "确定").trigger("click");
    expect(wrapper.emitted("confirm")).toEqual([["104.06,30.67"]]);
  });

  it("ignores config responses after closing the picker", async () => {
    let finish: (value: unknown) => void = () => {};
    vi.mocked(axiosInstance.get).mockReturnValueOnce(
      new Promise((resolve) => {
        finish = resolve;
      })
    );
    const wrapper = render();
    await nextTick();
    await wrapper.setProps({ visible: false });
    finish({ data: { gaode: { key: "test", securityJsCode: "test" } } });
    await flushPromises();
    expect(AMapLoader.load).not.toHaveBeenCalled();
  });

  it("opens above the coordinate field and fills FormKit only on confirm", async () => {
    const FormKit = defineComponent({
      props: ["name", "type", "modelValue"],
      emits: ["node"],
      mounted() {
        if (this.name === "coordinates")
          this.$emit("node", { input: nodeInput });
      },
      template: '<div :data-field="name"><slot/></div>',
    });
    const wrapper = mount(GroupEditingModal, {
      props: { visible: false },
      global: { stubs: { FormKit, AnnotationsForm: true } },
    });
    wrappers.push(wrapper);
    await wrapper.setProps({ visible: true });
    await flushPromises();
    expect(
      wrapper.find(".coordinates-label-row button").attributes("type")
    ).toBe("button");
    expect(
      wrapper
        .find(".coordinates-label-row")
        .element.nextElementSibling?.getAttribute("data-field")
    ).toBe("coordinates");
    await wrapper.find(".map-parser-button").trigger("click");
    const picker = wrapper.findComponent(GaodeModel);
    expect(picker.props("visible")).toBe(true);
    expect(nodeInput).not.toHaveBeenCalled();
    picker.vm.$emit("confirm", "104.06,30.67");
    await nextTick();
    expect(nodeInput).toHaveBeenCalledWith("104.06,30.67");
  });
});
