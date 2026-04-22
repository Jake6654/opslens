# build stage
FROM node:20-alpine AS build
WORKDIR /app

# optimize dependencies
COPY pakage.json pakage-lock.json ./
# install node modules
RUN npm install

COPY . .
RUN npm run build

# Run stage
FROM node:20-alpine
WORKDIR /app

COPY --from=build /app ./

EXPOSE 3000

CMD [ "npm", "start" ]