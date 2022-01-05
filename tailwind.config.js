module.exports = {
  content: ["./public/**/*.js"],
  darkMode: "media",
  theme: {
    extend: {},
    fontFamily: {
      sans: ["Fira Sans", "-apple-system", "BlinkMacSystemFont", "sans-serif"],
      serif: ["PTSerif", "serif"],
      mono: ["Fira Mono", "monospace"]
    }
  },
  variants: {
    extend: {},
  },
  plugins: [
    require("@tailwindcss/typography")
  ],
};
