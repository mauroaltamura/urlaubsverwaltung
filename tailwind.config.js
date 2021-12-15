const defaultConfig = require("tailwindcss/defaultConfig.js");

module.exports = {
  content: [
    "./src/main/webapp/**/*.jsp",
    "./src/main/webapp/**/*.tag",
    "./src/main/javascript/**/*.js",
    "./src/main/resources/templates/**/*.html",
  ],
  // use a prefix to not conflict with bootstrap
  prefix: "tw-",
  // use important keyword for tailwind utility classes to override bootstrap selectors
  important: true,
  corePlugins: {
    // disable tailwind base/reset styles. we're still using bootstrap
    preflight: false,
  },
  theme: {
    extend: {
      lineHeight: {
        normal: "normal",
      },
      fontSize: {
        "10rem": "10rem",
      },
      margin: {
        25: "6.25rem",
      },
      colors: {
        "black-almost": "#444444",
        "bootstrap-green": "#5cb85c",
        "bootstrap-green-dark": "#449d44",
      },
    },
    screens: {
      // cannot use 'extend' as `xs` would override other screens
      // since it's added to the bottom of the css file
      xs: "480px",
      ...defaultConfig.theme.screens,
    },
  },
  plugins: [],
};
