import "vue/jsx";

declare module "vue" {
  interface ComponentCustomProperties {
    $formkit: {
      submit(formId: string): void;
    };
  }
}
