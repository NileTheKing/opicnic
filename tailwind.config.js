// 템플릿에 흩어져 있던 tailwind.config(Play CDN)를 옮긴 것. 클래스는 템플릿 파일 글자에서 찾으므로 JS에서 조각을 이어 붙이면(`bg-${x}-100`) 빠진다.
module.exports = {
    content: ['./src/main/resources/templates/**/*.html'],
    theme: {
        extend: {
            colors: {
                brand: {
                    50:  '#f0f5fb',
                    100: '#d8e6f3',
                    200: '#b0cce7',
                    300: '#7dacd5',
                    400: '#528db8',
                    500: '#3a729f',
                    600: '#2e5c86',
                    700: '#234870',
                    800: '#17325a',
                    900: '#0e2040',
                }
            }
        }
    }
};
