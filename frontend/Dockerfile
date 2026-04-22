# build stage
FROM node:20-alpine AS build
WORKDIR /app


ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

# optimize dependencies
COPY package.json package-lock.json ./
# install node modules
RUN npm install

COPY . .
RUN npm run build

# Run stage
FROM node:20-alpine
WORKDIR /app

ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}

COPY --from=build /app ./

EXPOSE 3000

CMD [ "npm", "start" ]