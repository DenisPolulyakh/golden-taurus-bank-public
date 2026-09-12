import path from 'path'
import { defineConfig } from 'vite'

function inlineEverything() {
    return {
        name: 'inline-everything',
        enforce: 'post',
        generateBundle(options, bundle) {
            const chunk = Object.values(bundle).find((file) => file.type === 'chunk')
            const html = Object.values(bundle).find((file) => file.fileName.endsWith('.html'))
            if (!chunk || !html) return

            const tag = new RegExp(`<script[^>]*src="[^"]*${chunk.fileName.split('/').pop()}"[^>]*></script>`)
            html.source = String(html.source).replace(
                tag,
                `<script type="module">${chunk.code.replace(/<\/script>/g, '<\\/script>')}</script>`
            )

            delete bundle[chunk.fileName]
        },
    }
}

export default defineConfig({
    root: path.resolve(process.cwd(), 'src/opener'),
    base: './',
    plugins: [inlineEverything()],
    resolve: {
        alias: { '@': path.resolve(process.cwd(), 'src') },
    },
    build: {
        outDir: path.resolve(process.cwd(), 'opener-dist'),
        emptyOutDir: true,
        assetsInlineLimit: 100_000_000,
        cssCodeSplit: false,
        target: 'es2020',
        rollupOptions: { output: { inlineDynamicImports: true } },
    },
})
