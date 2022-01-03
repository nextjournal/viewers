module.exports = {
  mode: "jit",
  purge: ["./public/**/*.js"],
  darkMode: "media",
  theme: {
    extend: {},
    fontFamily: {
      sans: ["Fira Sans", "-apple-system", "BlinkMacSystemFont", "sans-serif"],
      serif: ["PTSerif", "serif"],
      mono: ["Fira Mono", "monospace"],
      display: ["Fira Sans Condensed", "-apple-system", "BlinkMacSystemFont", "sans-serif"],
      inter: ["Inter", "-apple-system", "BlinkMacSystemFont", "sans-serif"]
    }
  },
  variants: {
    extend: {},
  },
  plugins: [],
};
