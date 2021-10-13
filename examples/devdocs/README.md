# Example project for Devdocs

## Running the example

Make sure your checkout of the `viewers` repository is clean, in particular you
should not have a `node_modules` at the project root. This confuses Shadow-cljs.

``` shell
git clean -xfd
# or
rm -rf node_modules
```

You do need to install the npm-deps **inside `examples/devdocs`**. After that you
can run Shadow-cljs.

```
cd examples/devdocs
yarn install
npx shadow-cljs watch main
```

And browse the notbooks at [http://localhost:7799](localhost:7799).



