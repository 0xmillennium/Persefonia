declare module "*.css";

declare module "*.mp4?url" {
  const url: string;
  export default url;
}
