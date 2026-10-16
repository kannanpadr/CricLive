FROM mcr.microsoft.com/playwright:v1.64.0-noble

WORKDIR /app

COPY package*.json ./
RUN npm install --ignore-scripts

COPY . .

ENV PORT=10000
EXPOSE 10000

CMD ["node", "server.js"]
